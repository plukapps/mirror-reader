import Foundation
import ReaderDomain
import SwiftData

/// Base local de la biblioteca (ADR 0002, ADR 0012): la fuente de verdad de lo que muestra la app.
/// Es un `ModelActor`: todo acceso a SwiftData pasa por su propio contexto, fuera del hilo principal.
actor LibraryStore: ModelActor, LibraryRepository, CloudBooksRepository, BookFiles, ReadingPositionRepository {
    nonisolated let modelContainer: ModelContainer
    nonisolated let modelExecutor: any ModelExecutor
    private let files: LibraryFiles
    private let now: @Sendable () -> Date

    init(container: ModelContainer, files: LibraryFiles, now: @escaping @Sendable () -> Date = { .now }) {
        modelContainer = container
        modelExecutor = DefaultSerialModelExecutor(modelContext: ModelContext(container))
        self.files = files
        self.now = now
    }

    /// Base guardada en disco, en `Application Support/Library.store`.
    static func persistentContainer() throws -> ModelContainer {
        let configuration = ModelConfiguration("Library", schema: schema)
        return try ModelContainer(for: schema, configurations: configuration)
    }

    /// Base en memoria, para los tests.
    static func inMemoryContainer() throws -> ModelContainer {
        try ModelContainer(for: schema, configurations: ModelConfiguration(isStoredInMemoryOnly: true))
    }

    private static let schema = Schema([BookRecord.self, PositionRecord.self])

    // MARK: LibraryRepository

    func books() async -> [LibraryBook] {
        do {
            let records = try modelContext.fetch(FetchDescriptor<BookRecord>(sortBy: [SortDescriptor(\.addedAt, order: .reverse)]))
            let positions = try positionsByBook()
            return records.map { record in
                let position = positions[record.id]
                return LibraryBook(
                    id: record.id,
                    title: record.title,
                    author: record.author,
                    coverPath: record.hasCover ? files.coverFile(bookId: record.id).path : nil,
                    progressPercent: position.flatMap { progressPercent($0.progress) },
                    lastReadAt: position.map { Date(timeIntervalSince1970: Double($0.readAt) / 1000) },
                    addedAt: record.addedAt,
                    isDownloaded: record.isDownloaded
                )
            }
        } catch {
            return []
        }
    }

    // MARK: CloudBooksRepository

    func addCloudOnly(_ books: [RemoteBook]) async throws {
        let existing = Set(try modelContext.fetch(FetchDescriptor<BookRecord>()).map(\.id))
        let seenAt = now()
        for book in books where !existing.contains(book.id) {
            modelContext.insert(
                BookRecord(
                    id: book.id, title: book.title, author: book.joinedAuthors, hasCover: false,
                    addedAt: book.createdAt ?? seenAt, sizeBytes: book.sizeBytes, isDownloaded: false, uploadedAt: seenAt
                )
            )
        }
        try modelContext.save()
    }

    func applyRemotePositions(_ remote: [RemotePosition]) async throws -> Int {
        var local = try positionsByBook()
        var saved = 0
        for position in remote where shouldUseRemotePosition(localReadAt: local[position.bookId]?.readAt, remoteReadAt: position.readAt) {
            if let record = local[position.bookId] {
                record.locatorJson = position.locatorJson
                record.readAt = position.readAt
                record.progress = position.progress
                record.isSynced = true
            } else {
                let record = PositionRecord(
                    bookId: position.bookId, locatorJson: position.locatorJson, readAt: position.readAt,
                    progress: position.progress, isSynced: true
                )
                modelContext.insert(record)
                local[position.bookId] = record
            }
            saved += 1
        }
        try modelContext.save()
        return saved
    }

    // MARK: ReadingPositionRepository (RDR-006)

    func position(bookId: String) async -> SavedPosition? {
        guard let record = try? positionsByBook()[bookId] else { return nil }
        return SavedPosition(locatorJson: record.locatorJson, progress: record.progress)
    }

    func save(bookId: String, position: SavedPosition) async {
        let readAt = Int64((now().timeIntervalSince1970 * 1000).rounded())
        do {
            if let record = try positionsByBook()[bookId] {
                record.locatorJson = position.locatorJson
                record.progress = position.progress
                record.readAt = readAt
                record.isSynced = false
            } else {
                modelContext.insert(
                    PositionRecord(
                        bookId: bookId, locatorJson: position.locatorJson, readAt: readAt,
                        progress: position.progress, isSynced: false
                    )
                )
            }
            try modelContext.save()
        } catch {
            // Una posición que no se guarda no corta la lectura; la siguiente lo vuelve a intentar.
        }
    }

    // MARK: BookFiles (LIB-007)

    func isDownloaded(bookId: String) async -> Bool {
        guard (try? record(bookId))?.isDownloaded == true else { return false }
        return FileManager.default.fileExists(atPath: files.bookFile(bookId: bookId).path)
    }

    func installBook(bookId: String, from file: URL) async throws {
        try files.installBook(bookId: bookId, from: file)
        try record(bookId)?.isDownloaded = true
        try modelContext.save()
    }

    func booksWithoutCover() async throws -> [String] {
        try modelContext.fetch(FetchDescriptor<BookRecord>(predicate: #Predicate { !$0.hasCover })).map(\.id)
    }

    nonisolated func newTempFile() -> URL { files.newTempFile() }

    func installCover(bookId: String, from file: URL) async throws {
        try files.installCover(bookId: bookId, from: file)
        let records = try modelContext.fetch(FetchDescriptor<BookRecord>(predicate: #Predicate { $0.id == bookId }))
        records.forEach { $0.hasCover = true }
        try modelContext.save()
    }

    // MARK: Libros importados (LIB-001 a LIB-004)

    /// Lo que la importación necesita saber de un libro que ya está en la base, o nil si no está.
    func importedBook(id: String) -> (title: String, isDownloaded: Bool)? {
        guard let book = try? record(id) else { return nil }
        return (book.title, book.isDownloaded)
    }

    /// Registra un libro recién importado, con su archivo ya en `books/{id}.epub`.
    func addImported(id: String, title: String, author: String?, hasCover: Bool, sizeBytes: Int64) throws {
        modelContext.insert(
            BookRecord(
                id: id, title: title, author: author, hasCover: hasCover, addedAt: now(),
                sizeBytes: sizeBytes, isDownloaded: true, uploadedAt: nil
            )
        )
        try modelContext.save()
    }

    /// Un libro solo en la nube que el usuario importó a mano: conserva los metadatos de la nube y suma el archivo
    /// y la portada, como `markDownloaded` de Android.
    func markImported(id: String, hasCover: Bool) throws {
        guard let book = try record(id) else { return }
        book.isDownloaded = true
        book.hasCover = book.hasCover || hasCover
        try modelContext.save()
    }

    /// Título del libro, para el lector.
    func title(bookId: String) -> String? {
        (try? record(bookId))?.title
    }

    private func record(_ id: String) throws -> BookRecord? {
        try modelContext.fetch(FetchDescriptor<BookRecord>(predicate: #Predicate { $0.id == id })).first
    }

    private func positionsByBook() throws -> [String: PositionRecord] {
        let positions = try modelContext.fetch(FetchDescriptor<PositionRecord>())
        return Dictionary(positions.map { ($0.bookId, $0) }, uniquingKeysWith: { first, _ in first })
    }
}
