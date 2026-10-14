import Foundation

/// Nota sobre un texto del libro (ANN-003). El texto queda subrayado en el lector (ANN-008). Como `Note` de Android.
///
/// - `locatorJson`: lugar del texto en el EPUB, serializado como la posición de lectura: el dominio no lo interpreta.
/// - `quote`: el texto citado, para mostrarlo y reubicarlo si el lugar ya no resuelve (ANN-006).
/// - `progression`: progresión total 0...1 del lugar, para ordenar y mostrar el porcentaje (ANN-010).
/// - `createdAt`, `updatedAt`: milisegundos desde 1970, reloj del dispositivo.
public struct Note: Equatable, Sendable, Identifiable {
    public let id: String
    public let bookId: String
    public let locatorJson: String
    public let quote: String
    public var text: String
    public let progression: Double
    public let createdAt: Int64
    public var updatedAt: Int64

    public init(
        id: String, bookId: String, locatorJson: String, quote: String, text: String, progression: Double,
        createdAt: Int64, updatedAt: Int64
    ) {
        self.id = id
        self.bookId = bookId
        self.locatorJson = locatorJson
        self.quote = quote
        self.text = text
        self.progression = progression
        self.createdAt = createdAt
        self.updatedAt = updatedAt
    }

    /// Porcentaje del libro donde está la nota (ANN-010).
    public var percent: Int { Int(min(max(progression, 0), 1) * 100) }
}

/// Notas de los libros en el dispositivo (ANN-003). La implementación vive en la capa de datos de la app.
public protocol NoteRepository: Sendable {
    /// Notas del libro, en el orden del libro; a igual lugar, la más vieja primero (ANN-010).
    func notes(bookId: String) async -> [Note]
    func note(id: String) async -> Note?
    /// Crea la nota o reemplaza su texto si ya existe (ANN-005).
    func save(_ note: Note) async
    func delete(id: String) async
}
