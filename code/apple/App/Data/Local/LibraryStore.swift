import Foundation
import ReaderDomain
import SwiftData

/// Base local de la biblioteca (ADR 0002, ADR 0012): la fuente de verdad de lo que muestra la app.
/// Es un `ModelActor`: todo acceso a SwiftData pasa por su propio contexto, fuera del hilo principal.
actor LibraryStore: ModelActor, LibraryRepository, CloudBooksRepository {
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

    private func positionsByBook() throws -> [String: PositionRecord] {
        let positions = try modelContext.fetch(FetchDescriptor<PositionRecord>())
        return Dictionary(positions.map { ($0.bookId, $0) }, uniquingKeysWith: { first, _ in first })
    }
}
