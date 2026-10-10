import Foundation
import SwiftData

/// Libro de la biblioteca, como `BookEntity` de Android. `id` es el SHA-256 del contenido (LIB-003, ADR 0006).
@Model
final class BookRecord {
    @Attribute(.unique) var id: String
    var title: String
    var author: String?
    /// Hay portada en `covers/{id}.jpg`.
    var hasCover: Bool
    var addedAt: Date
    var sizeBytes: Int64
    /// El archivo está en este dispositivo. Falso: libro solo en la nube (LIB-007).
    var isDownloaded: Bool
    /// Instante en que se subió o se vio en la nube, o nil si aún no está subido.
    var uploadedAt: Date?
    /// La portada ya está en la nube (LIB-012). Con valor por defecto: SwiftData migra la base sola (ADR 0012).
    var isCoverUploaded: Bool = false

    init(
        id: String, title: String, author: String?, hasCover: Bool, addedAt: Date,
        sizeBytes: Int64, isDownloaded: Bool, uploadedAt: Date?, isCoverUploaded: Bool = false
    ) {
        self.id = id
        self.title = title
        self.author = author
        self.hasCover = hasCover
        self.addedAt = addedAt
        self.sizeBytes = sizeBytes
        self.isDownloaded = isDownloaded
        self.uploadedAt = uploadedAt
        self.isCoverUploaded = isCoverUploaded
    }
}

/// Última posición de lectura de un libro, como `ReadingPositionEntity` de Android (RDR-006, ADR 0011).
@Model
final class PositionRecord {
    @Attribute(.unique) var bookId: String
    var locatorJson: String
    /// Milisegundos desde 1970 en que se leyó, según el reloj del dispositivo (`readAt` en la nube, SYN-003).
    var readAt: Int64
    /// Progresión total 0...1, o nil si se desconoce (LIB-011).
    var progress: Double?
    /// La nube ya tiene esta lectura (SYN-011). Las que llegan de la nube ya lo están.
    var isSynced: Bool

    init(bookId: String, locatorJson: String, readAt: Int64, progress: Double?, isSynced: Bool) {
        self.bookId = bookId
        self.locatorJson = locatorJson
        self.readAt = readAt
        self.progress = progress
        self.isSynced = isSynced
    }
}
