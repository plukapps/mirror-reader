import Foundation
import ReaderDomain
import Testing
@testable import Reader

// Base local con SwiftData en memoria y carpetas temporales (ADR 0012).
struct LibraryStoreTests {
    private let files = LibraryFiles(root: FileManager.default.temporaryDirectory.appending(path: "LibraryStoreTests-\(UUID().uuidString)"))
    private let seenAt = Date(timeIntervalSince1970: 5_000)

    private func makeStore() throws -> LibraryStore {
        let seenAt = seenAt
        return LibraryStore(container: try LibraryStore.inMemoryContainer(), files: files, now: { seenAt })
    }

    private func remote(_ id: String, createdAt: Double? = nil) -> RemoteBook {
        RemoteBook(id: id, title: "T\(id)", authors: ["Ana", "Beto"], sizeBytes: 7, createdAt: createdAt.map(Date.init(timeIntervalSince1970:)))
    }

    @Test("LIB-007: un libro de la nube queda en la biblioteca local, sin portada ni progreso")
    func addsCloudOnlyBook() async throws {
        let store = try makeStore()
        try await store.addCloudOnly([remote("a", createdAt: 100)])

        let books = await store.books()
        #expect(books.count == 1)
        #expect(books[0].title == "Ta")
        #expect(books[0].author == "Ana, Beto")
        #expect(books[0].coverPath == nil)
        #expect(books[0].status == .new)
        #expect(books[0].addedAt == Date(timeIntervalSince1970: 100))
        #expect(!books[0].isDownloaded)
    }

    @Test("HOM-009: sin fecha de la nube, la fecha de alta es cuando se vio")
    func addedAtFallsBackToNow() async throws {
        let store = try makeStore()
        try await store.addCloudOnly([remote("a")])
        #expect(await store.books()[0].addedAt == seenAt)
    }

    @Test("LIB-003: agregar dos veces el mismo libro no lo duplica ni lo cambia")
    func addingTwiceKeepsOne() async throws {
        let store = try makeStore()
        try await store.addCloudOnly([remote("a", createdAt: 100)])
        try await store.addCloudOnly([RemoteBook(id: "a", title: "Otro", authors: [], sizeBytes: 1, createdAt: nil)])

        let books = await store.books()
        #expect(books.map(\.title) == ["Ta"])
    }

    @Test("HOM-009: el importado más reciente primero")
    func booksAreNewestFirst() async throws {
        let store = try makeStore()
        try await store.addCloudOnly([remote("viejo", createdAt: 100), remote("nuevo", createdAt: 300), remote("medio", createdAt: 200)])
        #expect(await store.books().map(\.id) == ["nuevo", "medio", "viejo"])
    }

    @Test("SYN-002: la posición de la nube da progreso y última lectura")
    func remotePositionGivesProgress() async throws {
        let store = try makeStore()
        try await store.addCloudOnly([remote("a")])
        let saved = try await store.applyRemotePositions([RemotePosition(bookId: "a", locatorJson: "{}", progress: 0.42, readAt: 2_000)])

        let book = try #require(await store.books().first)
        #expect(saved == 1)
        #expect(book.progressPercent == 42)
        #expect(book.status == .reading)
        #expect(book.lastReadAt == Date(timeIntervalSince1970: 2))
    }

    @Test("SYN-003: una posición más vieja no pisa a la guardada")
    func olderPositionIsIgnored() async throws {
        let store = try makeStore()
        try await store.addCloudOnly([remote("a")])
        _ = try await store.applyRemotePositions([RemotePosition(bookId: "a", locatorJson: "{}", progress: 0.8, readAt: 2_000)])
        let saved = try await store.applyRemotePositions([RemotePosition(bookId: "a", locatorJson: "{}", progress: 0.1, readAt: 1_000)])

        #expect(saved == 0)
        #expect(await store.books().first?.progressPercent == 80)
    }

    @Test("SYN-003: una posición más nueva reemplaza a la guardada")
    func newerPositionReplaces() async throws {
        let store = try makeStore()
        try await store.addCloudOnly([remote("a")])
        _ = try await store.applyRemotePositions([RemotePosition(bookId: "a", locatorJson: "{}", progress: 0.1, readAt: 1_000)])
        _ = try await store.applyRemotePositions([RemotePosition(bookId: "a", locatorJson: "{}", progress: 1.0, readAt: 3_000)])

        #expect(await store.books().first?.status == .finished)
    }

    @Test("LIB-012: al instalar la portada, el libro la muestra y deja de faltar")
    func installsCover() async throws {
        let store = try makeStore()
        try await store.addCloudOnly([remote("a"), remote("b")])
        #expect(Set(try await store.booksWithoutCover()) == ["a", "b"])

        let temp = store.newTempFile()
        try Data([0xFF, 0xD8]).write(to: temp)
        try await store.installCover(bookId: "a", from: temp)

        #expect(try await store.booksWithoutCover() == ["b"])
        let book = try #require(await store.books().first { $0.id == "a" })
        let path = try #require(book.coverPath)
        #expect(FileManager.default.fileExists(atPath: path))
        #expect(path.hasSuffix("covers/a.jpg"))
    }
}
