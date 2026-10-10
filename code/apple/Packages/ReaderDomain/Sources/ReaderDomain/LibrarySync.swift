import Foundation

/// Resultado de una pasada de sincronización de la biblioteca, como `SyncReport` de Android. Lo que no se pudo hacer
/// sigue pendiente y se reintenta en la próxima pasada (SYN-001).
public struct SyncReport: Equatable, Sendable {
    /// Cómo salió la subida de los libros del dispositivo.
    public var upload: UploadReport
    /// Libros de la nube que se bajaron al dispositivo.
    public var downloaded: Int
    /// Libros que no se pudieron bajar por otro motivo que la falta de conexión.
    public var downloadFailed: Int
    public var coversUploaded: Int
    public var coversDownloaded: Int
    /// No se pudo contactar la nube: la pasada se cortó.
    public var unreachable: Bool

    public init(
        upload: UploadReport = UploadReport(),
        downloaded: Int = 0,
        downloadFailed: Int = 0,
        coversUploaded: Int = 0,
        coversDownloaded: Int = 0,
        unreachable: Bool = false
    ) {
        self.upload = upload
        self.downloaded = downloaded
        self.downloadFailed = downloadFailed
        self.coversUploaded = coversUploaded
        self.coversDownloaded = coversDownloaded
        self.unreachable = unreachable
    }

    public var offline: Bool { unreachable || upload.unreachable }
    public var notEnoughSpace: Int { upload.notEnoughSpace }
    public var failed: Int { upload.failed + downloadFailed }

    /// Qué impidió dejarlo todo al día (SYN-008). Primero la falta de conexión, después los fallos, después el espacio.
    public var issue: SyncIssue? {
        if offline { return .offline }
        if failed > 0 { return .failed(failed) }
        if notEnoughSpace > 0 { return .notEnoughSpace(notEnoughSpace) }
        return nil
    }
}

/// Qué le impidió a la última pasada dejarlo todo al día (SYN-008).
public enum SyncIssue: Equatable, Sendable {
    case offline
    case notEnoughSpace(Int)
    case failed(Int)
}

/// Resultado de `LibrarySync.run`.
public enum LibrarySyncOutcome: Equatable, Sendable {
    /// Sin sesión y sin cuenta de desarrollo (o falló el inicio): la app sigue solo en local (ADR 0002).
    case noSession
    case done(SyncReport)
}

/// Envía lo que quedó pendiente de la posición de lectura (SYN-011). La pasada lo llama al terminar.
public protocol PositionFlusher: Sendable {
    func flush() async
}

/// Deja la biblioteca igual en la nube y en este dispositivo, como `SyncLibraryUseCase` de Android (SYN-001,
/// LIB-007): trae los libros de la nube, sube los importados que faltan (LIB-009 decide cuáles entran en la cuota),
/// sincroniza las portadas (LIB-012), baja los archivos que aún no están aquí y envía las posiciones de lectura
/// pendientes. Cada paso se puede repetir sin duplicar nada. Sin conexión corta la pasada.
///
/// Sin sesión, inicia la de la cuenta de desarrollo si está configurada (K-052).
public struct LibrarySync: Sendable {
    private let account: AccountRepository
    private let credentials: DevCredentials
    private let remote: RemoteLibrary
    private let covers: CoverStore
    private let cloud: CloudBooksRepository
    private let upload: UploadBooks
    private let download: DownloadBook
    private let positions: PositionFlusher

    public init(
        account: AccountRepository,
        credentials: DevCredentials,
        remote: RemoteLibrary,
        covers: CoverStore,
        cloud: CloudBooksRepository,
        upload: UploadBooks,
        download: DownloadBook,
        positions: PositionFlusher
    ) {
        self.account = account
        self.credentials = credentials
        self.remote = remote
        self.covers = covers
        self.cloud = cloud
        self.upload = upload
        self.download = download
        self.positions = positions
    }

    /// - Parameter onChange: se llama cada vez que la base local cambió (libros de la nube, subidas, portadas y cada
    ///   descarga), para que la pantalla no espere al final de la pasada.
    public func run(onChange: @Sendable () async -> Void = {}) async -> LibrarySyncOutcome {
        guard await ensureSession() else { return .noSession }
        do {
            try await cloud.addCloudOnly(try await remote.listBooks())
        } catch is RemoteUnavailableError {
            return .done(SyncReport(unreachable: true))
        } catch {
            // Otro fallo al listar no impide subir ni bajar lo que ya se conoce, como en Android.
        }
        await onChange()

        var report = SyncReport(upload: await upload())
        if report.upload.unreachable { return .done(report) }
        if report.upload.uploaded > 0 { await onChange() }

        let coverOutcome = await syncCovers()
        report.coversUploaded = coverOutcome.uploaded
        report.coversDownloaded = coverOutcome.downloaded
        if coverOutcome.downloaded > 0 { await onChange() }

        for bookId in (try? await cloud.cloudOnlyBookIds()) ?? [] {
            do {
                try await download.fetch(bookId: bookId)
                report.downloaded += 1
                await onChange()
            } catch is RemoteUnavailableError {
                report.unreachable = true
                return .done(report)
            } catch {
                report.downloadFailed += 1
            }
        }
        // Los libros recién subidos ya existen en la nube: ahora puede salir la posición que los esperaba (SYN-011).
        await positions.flush()
        return .done(report)
    }

    private func ensureSession() async -> Bool {
        if await account.currentUser() != nil { return true }
        guard credentials.isConfigured else { return false }
        return (try? await account.signIn(email: credentials.email, password: credentials.password)) != nil
    }

    /// Sube las portadas locales que aún no están en la nube y baja las que faltan aquí (LIB-012). Es secundario: un
    /// fallo en una portada no corta las demás. Sin conexión corta este paso.
    private func syncCovers() async -> (uploaded: Int, downloaded: Int) {
        var uploaded = 0
        for cover in (try? await cloud.coversToUpload()) ?? [] {
            do {
                try await covers.uploadCover(bookId: cover.bookId, from: cover.file)
                // Se anota para no consultar la nube por esta portada en las próximas pasadas.
                try await cloud.markCoverUploaded(bookId: cover.bookId)
                uploaded += 1
            } catch is RemoteUnavailableError {
                return (uploaded, 0)
            } catch {}
        }
        var downloaded = 0
        for bookId in (try? await cloud.booksWithoutCover()) ?? [] {
            let temp = cloud.newTempFile()
            defer { try? FileManager.default.removeItem(at: temp) }
            do {
                if try await covers.downloadCover(bookId: bookId, to: temp) {
                    try await cloud.installCover(bookId: bookId, from: temp)
                    downloaded += 1
                }
            } catch is RemoteUnavailableError {
                return (uploaded, downloaded)
            } catch {}
        }
        return (uploaded, downloaded)
    }
}
