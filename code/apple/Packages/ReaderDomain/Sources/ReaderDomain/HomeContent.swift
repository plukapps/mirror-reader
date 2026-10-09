import Foundation

public enum Greeting: Sendable {
    case morning, afternoon, night
}

/// HOM-001: 5–11 h mañana, 12–19 h tarde, el resto noche.
public func greeting(forHour hour: Int) -> Greeting {
    switch hour {
    case 5...11: .morning
    case 12...19: .afternoon
    default: .night
    }
}

/// Máximo de libros por fila de Inicio (HOM-008 a HOM-010).
public let homeRowLimit = 5

/// Lo que muestra Inicio. Los `*Count` son el total de cada sección, no solo los visibles.
public struct HomeContent: Equatable, Sendable {
    /// Libro en lectura abierto más recientemente (HOM-002), o nil (HOM-003).
    public let continueReading: LibraryBook?
    /// Otros libros en lectura, hasta `homeRowLimit` (HOM-008).
    public let reading: [LibraryBook]
    public let readingCount: Int
    /// Últimos libros importados, de cualquier estado (HOM-009).
    public let recentlyAdded: [LibraryBook]
    public let recentlyAddedCount: Int
    /// Últimos libros terminados (HOM-010).
    public let finished: [LibraryBook]
    public let finishedCount: Int
    public let libraryEmpty: Bool

    public static let empty = homeContent([])
}

/// `books` llega con el importado más reciente primero. `sorted(by:)` es estable, así que los empates
/// conservan ese orden.
public func homeContent(_ books: [LibraryBook]) -> HomeContent {
    let byLastRead: (LibraryBook, LibraryBook) -> Bool = {
        ($0.lastReadAt ?? .distantPast) > ($1.lastReadAt ?? .distantPast)
    }
    let inProgress = books.filter { $0.status == .reading }.sorted(by: byLastRead)
    let others = Array(inProgress.dropFirst())
    let finished = books.filter { $0.status == .finished }.sorted(by: byLastRead)
    return HomeContent(
        continueReading: inProgress.first,
        reading: Array(others.prefix(homeRowLimit)),
        readingCount: others.count,
        recentlyAdded: Array(books.sorted { $0.addedAt > $1.addedAt }.prefix(homeRowLimit)),
        recentlyAddedCount: books.count,
        finished: Array(finished.prefix(homeRowLimit)),
        finishedCount: finished.count,
        libraryEmpty: books.isEmpty
    )
}
