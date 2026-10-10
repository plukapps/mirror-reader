import Foundation

public enum ReadingStatus: Sendable {
    case new, reading, finished
}

/// Libro de la biblioteca tal como lo muestran la grilla e Inicio (LIB-011).
public struct LibraryBook: Identifiable, Equatable, Sendable {
    /// Hash del contenido (LIB-003).
    public let id: String
    public let title: String
    public let author: String?
    /// Ruta del archivo de portada, o nil si el EPUB no trae una.
    public let coverPath: String?
    /// 0...100, o nil si el libro nunca se abrió.
    public let progressPercent: Int?
    /// Última posición guardada, o nil si nunca se abrió (HOM-002).
    public let lastReadAt: Date?
    /// Momento de la importación (HOM-009).
    public let addedAt: Date
    /// El archivo está en este dispositivo. Falso: solo en la nube (LIB-007).
    public let isDownloaded: Bool

    public var status: ReadingStatus { readingStatus(progressPercent: progressPercent) }

    public init(
        id: String,
        title: String,
        author: String?,
        coverPath: String?,
        progressPercent: Int?,
        lastReadAt: Date? = nil,
        addedAt: Date = Date(timeIntervalSince1970: 0),
        isDownloaded: Bool = true
    ) {
        self.id = id
        self.title = title
        self.author = author
        self.coverPath = coverPath
        self.progressPercent = progressPercent
        self.lastReadAt = lastReadAt
        self.addedAt = addedAt
        self.isDownloaded = isDownloaded
    }
}

/// Sin progreso: nuevo. Al 100 %: terminado. Cualquier otro caso: leyendo (LIB-010, LIB-011).
public func readingStatus(progressPercent: Int?) -> ReadingStatus {
    guard let progressPercent else { return .new }
    return progressPercent >= 100 ? .finished : .reading
}

/// Filtro de la biblioteca por estado de lectura (LIB-010).
public enum LibraryFilter: CaseIterable, Sendable {
    case all, reading, finished
}

public extension Array where Element == LibraryBook {
    /// Los libros que pasan el filtro, en el mismo orden.
    func filter(by filter: LibraryFilter) -> [LibraryBook] {
        switch filter {
        case .all: self
        case .reading: self.filter { $0.status == .reading }
        case .finished: self.filter { $0.status == .finished }
        }
    }

    /// Cantidades para las pestañas "Todos N", "Leyendo N" y "Terminados N".
    func count(by filter: LibraryFilter) -> Int { self.filter(by: filter).count }
}
