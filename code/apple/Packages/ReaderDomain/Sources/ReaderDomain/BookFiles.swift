import Foundation

/// No se pudo abrir o importar un libro. `message` está listo para mostrarse.
public struct BookOpenError: Error, Equatable, Sendable {
    public let message: String
    public init(_ message: String) { self.message = message }
}

/// Resultado de importar un EPUB (LIB-001 a LIB-004), como `ImportOutcome` de Android.
public enum ImportOutcome: Equatable, Sendable {
    case imported(bookId: String, title: String)
    /// El mismo contenido ya estaba en la biblioteca: no se duplica (LIB-003).
    case alreadyInLibrary(bookId: String, title: String)
    /// Corrupto, con DRM o ilegible (LIB-002). `message` está listo para mostrarse.
    case rejected(message: String)
}

/// Importa un archivo EPUB elegido por el usuario. La implementación vive en la capa de datos de la app.
public protocol BookImporter: Sendable {
    func importBook(from url: URL) async -> ImportOutcome
}

/// Archivos de libros en el dispositivo. La implementación vive en la capa de datos de la app.
public protocol BookFiles: Sendable {
    /// El archivo del libro está en el dispositivo (falso: solo en la nube, LIB-007).
    func isDownloaded(bookId: String) async -> Bool
    /// Archivo temporal donde bajar algo antes de instalarlo.
    func newTempFile() -> URL
    /// Mueve el archivo bajado a su lugar y marca el libro como descargado.
    func installBook(bookId: String, from file: URL) async throws
}

/// Archivos EPUB en la nube (`users/{uid}/books/{bookId}.epub`). Lanza `RemoteUnavailableError` sin conexión o sesión.
public protocol BookFileStore: Sendable {
    func downloadBook(bookId: String, to destination: URL) async throws
}

/// Descarga un libro que está solo en la nube (LIB-007), como `DownloadBookUseCase` de Android. Si el archivo ya
/// está en el dispositivo no hace nada. Baja a un temporal y recién al terminar lo instala: una descarga cortada no
/// deja un libro a medias. El fallo es un `BookOpenError` con mensaje apto para el usuario.
public struct DownloadBook: Sendable {
    private let files: BookFiles
    private let store: BookFileStore

    public init(files: BookFiles, store: BookFileStore) {
        self.files = files
        self.store = store
    }

    public func callAsFunction(bookId: String) async throws {
        if await files.isDownloaded(bookId: bookId) { return }
        let temp = files.newTempFile()
        defer { try? FileManager.default.removeItem(at: temp) }
        do {
            try await store.downloadBook(bookId: bookId, to: temp)
            try await files.installBook(bookId: bookId, from: temp)
        } catch is RemoteUnavailableError {
            throw BookOpenError("Sin conexión. No se pudo descargar el libro.")
        } catch {
            throw BookOpenError("No se pudo descargar el libro.")
        }
    }
}

/// Última posición guardada de un libro (RDR-006): locator de Readium en JSON y progresión total 0...1.
public struct SavedPosition: Equatable, Sendable {
    public let locatorJson: String
    public let progress: Double?

    public init(locatorJson: String, progress: Double?) {
        self.locatorJson = locatorJson
        self.progress = progress
    }
}

/// Posición de lectura en el dispositivo. La implementación vive en la capa de datos de la app.
public protocol ReadingPositionRepository: Sendable {
    func position(bookId: String) async -> SavedPosition?
    /// Guarda la posición como la lectura más reciente de este dispositivo (`readAt` = ahora, sin sincronizar).
    func save(bookId: String, position: SavedPosition) async
}
