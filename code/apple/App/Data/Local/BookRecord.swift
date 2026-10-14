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
    /// Instante (ms desde 1970) en que el usuario reemplazó la portada, o 0 (LIB-019). La ruta del archivo no cambia.
    var coverVersion: Int64 = 0

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

/// Libro quitado de la biblioteca (LIB-020). La sincronización no lo vuelve a agregar; importar el mismo archivo
/// borra la marca. Como `RemovedBookEntity` de Android.
@Model
final class RemovedBookRecord {
    @Attribute(.unique) var id: String
    var removedAt: Date
    /// La nube ya lo tiene borrado (marca y archivos), o nunca estuvo allí. Falso: falta enviarlo.
    var isCloudDeleted: Bool = false

    init(id: String, removedAt: Date, isCloudDeleted: Bool = false) {
        self.id = id
        self.removedAt = removedAt
        self.isCloudDeleted = isCloudDeleted
    }
}

/// Nota sobre un texto del libro (ANN-003, ANN-006), como `NoteEntity` de Android. `id` es un UUID, el mismo que tendrá
/// en la nube. Modelo nuevo: SwiftData lo agrega solo a la base existente (ADR 0012).
@Model
final class NoteRecord {
    @Attribute(.unique) var id: String
    var bookId: String
    var locatorJson: String
    var quote: String
    var text: String
    var progression: Double
    /// Milisegundos desde 1970, reloj del dispositivo.
    var createdAt: Int64
    /// Última edición o borrado, en milisegundos desde 1970 (`editedAt` en la nube): decide quién gana (SYN-005).
    var updatedAt: Int64
    /// Marca de borrado (SYN-007). Las pantallas no ven las notas borradas. Con valor por defecto: migración automática.
    var deletedAt: Int64? = nil
    /// El `updatedAt` que la nube ya tiene, o nil si la nota nunca llegó a la nube. Menor que `updatedAt`: hay un
    /// cambio pendiente de subir (SYN-016).
    var syncedAt: Int64? = nil

    init(
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
}
