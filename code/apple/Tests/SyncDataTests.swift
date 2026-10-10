import CryptoKit
import Foundation
import ReaderDomain
import Testing
@testable import Reader

// Base local para la sincronización de libros (plan `2026-10-10-ios-sync.md`, K-148).
struct SyncDataTests {
    private let files = LibraryFiles(root: FileManager.default.temporaryDirectory.appending(path: "SyncDataTests-\(UUID().uuidString)"))
    private let clock = TestClock()

    private func makeStore() throws -> LibraryStore {
        let clock = clock
        return LibraryStore(container: try LibraryStore.inMemoryContainer(), files: files, now: { clock.now })
    }

    /// Importa un libro como lo hace `EpubImporter`: archivo en su lugar y registro sin subir.
    private func importBook(_ store: LibraryStore, _ id: String, content: String = "epub", cover: Bool = false) async throws {
        try files.installBook(bookId: id, from: try tempFile(content))
        if cover { try files.installCover(bookId: id, from: try tempFile("jpg")) }
        try await store.addImported(id: id, title: "T\(id)", author: "Ana, Luis", hasCover: cover, sizeBytes: 0)
    }

    private func tempFile(_ text: String) throws -> URL {
        let url = files.newTempFile()
        try Data(text.utf8).write(to: url)
        return url
    }

    private func sha256(_ text: String) -> String {
        SHA256.hash(data: Data(text.utf8)).map { String(format: "%02x", $0) }.joined()
    }

    @Test("SYN-001: los importados sin subir quedan pendientes, el más viejo primero, con el tamaño real del archivo")
    func pendingUploads() async throws {
        let store = try makeStore()
        try await importBook(store, "a", content: "12345")
        clock.advance()
        try await importBook(store, "b", cover: true)
        try await store.addCloudOnly([RemoteBook(id: "c", title: "Nube", authors: [], sizeBytes: 9)])

        let pending = try await store.pending()

        #expect(pending.map(\.book.id) == ["a", "b"])
        // Como Android: el autor local es un texto y viaja como un solo elemento de la lista.
        #expect(pending[0].book == RemoteBook(id: "a", title: "Ta", authors: ["Ana, Luis"], sizeBytes: 5))
        #expect(pending[0].file == files.bookFile(bookId: "a"))
        #expect(pending[0].cover == nil)
        #expect(pending[1].cover == files.coverFile(bookId: "b"))
    }

    @Test("SYN-001: un libro que perdió su archivo no se intenta subir")
    func pendingSkipsMissingFile() async throws {
        let store = try makeStore()
        try await importBook(store, "a")
        try FileManager.default.removeItem(at: files.bookFile(bookId: "a"))
        #expect(try await store.pending().isEmpty)
    }

    @Test("SYN-001: un libro subido deja de estar pendiente y su portada no se vuelve a subir")
    func markUploaded() async throws {
        let store = try makeStore()
        try await importBook(store, "a", cover: true)
        try await store.markUploaded(bookId: "a", sizeBytes: 4, coverUploaded: true)
        #expect(try await store.pending().isEmpty)
        #expect(try await store.coversToUpload().isEmpty)
        #expect(await store.books().first?.isUploaded == true)
    }

    @Test("LIB-012: la portada de un libro subido sin ella queda para subir hasta que se anota")
    func coversToUpload() async throws {
        let store = try makeStore()
        try await importBook(store, "a", cover: true)
        #expect(try await store.coversToUpload().isEmpty, "antes de subir el libro, la portada espera")
        try await store.markUploaded(bookId: "a", sizeBytes: 4, coverUploaded: false)

        #expect(try await store.coversToUpload() == [PendingCover(bookId: "a", file: files.coverFile(bookId: "a"))])
        try await store.markCoverUploaded(bookId: "a")
        #expect(try await store.coversToUpload().isEmpty)
    }

    @Test("LIB-012: una portada bajada de la nube no se vuelve a subir")
    func downloadedCoverIsNotUploaded() async throws {
        let store = try makeStore()
        try await store.addCloudOnly([RemoteBook(id: "a", title: "Nube", authors: [], sizeBytes: 9)])
        try await store.installCover(bookId: "a", from: try tempFile("jpg"))
        #expect(try await store.coversToUpload().isEmpty)
    }

    @Test("LIB-007: los libros solo en la nube se listan para bajarlos")
    func cloudOnlyIds() async throws {
        let store = try makeStore()
        try await importBook(store, "a")
        try await store.addCloudOnly([RemoteBook(id: "b", title: "Nube", authors: [], sizeBytes: 9)])
        #expect(try await store.cloudOnlyBookIds() == ["b"])
    }

    @Test("LIB-002: un archivo bajado que no coincide con el libro no se instala")
    func rejectsWrongHash() async throws {
        let store = try makeStore()
        let id = sha256("bueno")
        try await store.addCloudOnly([RemoteBook(id: id, title: "Nube", authors: [], sizeBytes: 5)])

        await #expect(throws: (any Error).self) { try await store.installBook(bookId: id, from: try tempFile("malo")) }
        #expect(await !store.isDownloaded(bookId: id))

        try await store.installBook(bookId: id, from: try tempFile("bueno"))
        #expect(await store.isDownloaded(bookId: id))
    }

    @Test("SYN-008: la biblioteca distingue los libros que aún no se subieron")
    func booksKnowIfUploaded() async throws {
        let store = try makeStore()
        try await importBook(store, "a")
        try await store.addCloudOnly([RemoteBook(id: "c", title: "Nube", authors: [], sizeBytes: 9)])
        let uploaded = Dictionary(uniqueKeysWithValues: await store.books().map { ($0.id, $0.isUploaded) })
        #expect(uploaded == ["a": false, "c": true])
    }
}

/// Reloj que avanza a mano, para ordenar libros por fecha de alta.
final class TestClock: @unchecked Sendable {
    private let lock = NSLock()
    private var current = Date(timeIntervalSince1970: 1_000)

    var now: Date { lock.withLock { current } }
    func advance(_ seconds: TimeInterval = 1) { lock.withLock { current += seconds } }
}
