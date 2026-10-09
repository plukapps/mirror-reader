import Foundation

/// Metadatos de un libro en la nube (`users/{uid}/books/{bookId}`). `id` es el hash del contenido (LIB-003).
public struct RemoteBook: Equatable, Sendable {
    public let id: String
    public let title: String
    public let authors: [String]
    public let sizeBytes: Int64
    /// Cuándo se subió (hora del servidor), o nil si el documento no la trae.
    public let createdAt: Date?

    public init(id: String, title: String, authors: [String], sizeBytes: Int64, createdAt: Date? = nil) {
        self.id = id
        self.title = title
        self.authors = authors
        self.sizeBytes = sizeBytes
        self.createdAt = createdAt
    }

    /// El autor local es un texto; en la nube, una lista (ver `backend.md`). Nil si no hay ninguno.
    public var joinedAuthors: String? {
        let names = authors.map { $0.trimmingCharacters(in: .whitespaces) }.filter { !$0.isEmpty }
        return names.isEmpty ? nil : names.joined(separator: ", ")
    }
}

/// Última posición de lectura de un libro en la nube (`users/{uid}/positions/{bookId}`, ADR 0011).
public struct RemotePosition: Equatable, Sendable {
    public let bookId: String
    /// Locator serializado por el lector que la escribió. Apple todavía no lo interpreta.
    public let locatorJson: String
    /// Progresión total 0...1, o nil si se desconoce.
    public let progress: Double?
    /// Milisegundos desde 1970 en que se leyó, según el reloj del dispositivo (SYN-003).
    public let readAt: Int64

    public init(bookId: String, locatorJson: String, progress: Double?, readAt: Int64) {
        self.bookId = bookId
        self.locatorJson = locatorJson
        self.progress = progress
        self.readAt = readAt
    }
}

/// La nube no se pudo alcanzar (sin red, sin sesión, error del servicio). No cambia nada local (ADR 0002).
public struct RemoteUnavailableError: Error, Equatable, Sendable {
    public let message: String
    public init(_ message: String) { self.message = message }
}

/// Metadatos de la biblioteca en la nube. Lanza `RemoteUnavailableError` si no hay conexión o sesión.
public protocol RemoteLibrary: Sendable {
    /// Libros vivos (no marcados como borrados, SYN-007) del usuario actual.
    func listBooks() async throws -> [RemoteBook]
}

/// Posiciones de lectura en la nube (SYN-002). Por ahora Apple solo las lee.
public protocol RemotePositions: Sendable {
    func listPositions() async throws -> [RemotePosition]
}

/// Portadas en la nube (LIB-012).
public protocol CoverStore: Sendable {
    /// Baja la portada a `destination`. `true` si la había; `false` si el libro no tiene portada en la nube.
    func downloadCover(bookId: String, to destination: URL) async throws -> Bool
}
