import Foundation

/// La nube rechazó un archivo porque no entra en la cuota del usuario (LIB-009).
public struct QuotaExceededError: Error, Equatable, Sendable {
    public init() {}
}

/// Espacio de la cuenta (`users/{uid}`, ACC-003). `usedBytes` lo mantiene el servidor (ADR 0008).
public struct StorageQuota: Equatable, Sendable {
    public let usedBytes: Int64
    public let quotaBytes: Int64

    public init(usedBytes: Int64, quotaBytes: Int64) {
        self.usedBytes = usedBytes
        self.quotaBytes = quotaBytes
    }

    public var freeBytes: Int64 { max(quotaBytes - usedBytes, 0) }
}

/// Cuota de la cuenta, leída del servidor. Lanza `RemoteUnavailableError` sin conexión, sin sesión o si la cuenta
/// todavía no está lista.
public protocol QuotaSource: Sendable {
    func current() async throws -> StorageQuota
}

/// Un libro con archivo en este dispositivo que aún no está en la nube. `book.sizeBytes` es el tamaño real del
/// archivo. `cover` es su portada local, o nil si no tiene (LIB-012).
public struct PendingUpload: Equatable, Sendable {
    public let book: RemoteBook
    public let file: URL
    public let cover: URL?

    public init(book: RemoteBook, file: URL, cover: URL? = nil) {
        self.book = book
        self.file = file
        self.cover = cover
    }
}

/// Libros pendientes de subir. La implementación vive en la capa de datos de la app.
public protocol BookUploadRepository: Sendable {
    /// Pendientes de subir, el más viejo primero. Se omiten los que perdieron su archivo local.
    func pending() async throws -> [PendingUpload]
    /// Registra que el libro ya está en la nube. `coverUploaded`: su portada también (LIB-012).
    func markUploaded(bookId: String, sizeBytes: Int64, coverUploaded: Bool) async throws
}

/// Resultado de una tanda de subida. Lo que no se subió sigue pendiente y no se pierde nada (SYN-001).
public struct UploadReport: Equatable, Sendable {
    public var uploaded: Int
    /// Libros que no entraron en la cuota (LIB-009).
    public var notEnoughSpace: Int
    /// Libros que fallaron por otro motivo.
    public var failed: Int
    /// No se pudo contactar la nube: la tanda se cortó.
    public var unreachable: Bool

    public init(uploaded: Int = 0, notEnoughSpace: Int = 0, failed: Int = 0, unreachable: Bool = false) {
        self.uploaded = uploaded
        self.notEnoughSpace = notEnoughSpace
        self.failed = failed
        self.unreachable = unreachable
    }

    public var nothingToDo: Bool { self == UploadReport() }
}

/// Sube a la nube los libros importados que aún no están, como `UploadBooksUseCase` de Android. Primero el archivo,
/// que la regla del servidor valida (tipo, tamaño, cuota), después los metadatos y la portada. Reintentar es seguro:
/// el archivo no se sobrescribe y el documento se vuelve a escribir.
public struct UploadBooks: Sendable {
    private let uploads: BookUploadRepository
    private let files: BookFileStore
    private let covers: CoverStore
    private let library: RemoteLibrary
    private let quota: QuotaSource

    public init(
        uploads: BookUploadRepository,
        files: BookFileStore,
        covers: CoverStore,
        library: RemoteLibrary,
        quota: QuotaSource
    ) {
        self.uploads = uploads
        self.files = files
        self.covers = covers
        self.library = library
        self.quota = quota
    }

    private enum Step { case uploaded, noSpace, failed, unreachable }

    public func callAsFunction() async -> UploadReport {
        guard let pending = try? await uploads.pending(), !pending.isEmpty else { return UploadReport() }
        // El servidor suma el uso unos segundos después de cada subida: el espacio libre se lleva aquí.
        guard var free = try? await quota.current().freeBytes else { return UploadReport(unreachable: true) }

        var report = UploadReport()
        for item in pending {
            if item.book.sizeBytes > free {
                report.notEnoughSpace += 1
                continue
            }
            switch await upload(item) {
            case .uploaded:
                report.uploaded += 1
                free -= item.book.sizeBytes
            case .noSpace:
                report.notEnoughSpace += 1
            case .failed:
                report.failed += 1
            case .unreachable:
                report.unreachable = true
                return report
            }
        }
        return report
    }

    private func upload(_ item: PendingUpload) async -> Step {
        let id = item.book.id
        do {
            try await files.uploadBook(bookId: id, from: item.file)
            try await library.saveBook(item.book)
        } catch {
            return step(for: error)
        }
        // La portada es secundaria (LIB-012): solo la falta de conexión deja el libro pendiente. Otro fallo se ignora
        // y la pasada de portadas la completa después.
        var coverUploaded = false
        if let cover = item.cover {
            do {
                try await covers.uploadCover(bookId: id, from: cover)
                coverUploaded = true
            } catch is RemoteUnavailableError {
                return .unreachable
            } catch {}
        }
        do {
            try await uploads.markUploaded(bookId: id, sizeBytes: item.book.sizeBytes, coverUploaded: coverUploaded)
        } catch {
            return .failed
        }
        return .uploaded
    }

    private func step(for error: any Error) -> Step {
        switch error {
        case is RemoteUnavailableError: .unreachable
        case is QuotaExceededError: .noSpace
        default: .failed
        }
    }
}
