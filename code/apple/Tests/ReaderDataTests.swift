import Foundation
import ReaderDomain
import Testing
@testable import Reader

// Datos del lector: posición, archivos de libros y ajustes (plan `2026-10-09-ios-reader.md`, K-130).
struct ReaderDataTests {
    private let files = LibraryFiles(root: FileManager.default.temporaryDirectory.appending(path: "ReaderDataTests-\(UUID().uuidString)"))
    private let readAt = Date(timeIntervalSince1970: 7_000)

    private func makeStore() throws -> LibraryStore {
        let readAt = readAt
        return LibraryStore(container: try LibraryStore.inMemoryContainer(), files: files, now: { readAt })
    }

    private func tempFile(_ text: String) throws -> URL {
        let url = files.newTempFile()
        try Data(text.utf8).write(to: url)
        return url
    }

    @Test("RDR-006: la posición guardada vuelve al abrir y da el progreso del libro")
    func savesAndReadsPosition() async throws {
        let store = try makeStore()
        try await store.addImported(id: "a", title: "Walden", author: nil, hasCover: false, sizeBytes: 1)
        #expect(await store.position(bookId: "a") == nil)

        await store.save(bookId: "a", position: SavedPosition(locatorJson: "{\"href\":\"c1\"}", progress: 0.25))
        await store.save(bookId: "a", position: SavedPosition(locatorJson: "{\"href\":\"c2\"}", progress: 0.5))

        #expect(await store.position(bookId: "a") == SavedPosition(locatorJson: "{\"href\":\"c2\"}", progress: 0.5))
        let book = await store.books()[0]
        #expect(book.progressPercent == 50)
        #expect(book.lastReadAt == readAt)
    }

    @Test("SYN-003: una lectura propia queda más nueva que la de la nube con el mismo momento anterior")
    func ownReadingIsNewerThanOlderRemote() async throws {
        let store = try makeStore()
        await store.save(bookId: "a", position: SavedPosition(locatorJson: "local", progress: 0.1))
        let remote = RemotePosition(bookId: "a", locatorJson: "remota", progress: 0.9, readAt: 6_000_000)
        #expect(try await store.applyRemotePositions([remote]) == 0)
        #expect(await store.position(bookId: "a")?.locatorJson == "local")
    }

    @Test("LIB-007: un libro solo en la nube no está descargado hasta instalar su archivo")
    func installingBookMarksItDownloaded() async throws {
        let store = try makeStore()
        try await store.addCloudOnly([RemoteBook(id: "a", title: "Walden", authors: [], sizeBytes: 3)])
        #expect(await !store.isDownloaded(bookId: "a"))

        try await store.installBook(bookId: "a", from: try tempFile("epub"))

        #expect(await store.isDownloaded(bookId: "a"))
        #expect(try String(contentsOf: files.bookFile(bookId: "a"), encoding: .utf8) == "epub")
    }

    @Test("LIB-007: si el archivo se borró, el libro vuelve a bajarse")
    func missingFileIsNotDownloaded() async throws {
        let store = try makeStore()
        try await store.addImported(id: "a", title: "Walden", author: nil, hasCover: false, sizeBytes: 1)
        #expect(await !store.isDownloaded(bookId: "a"))
    }

    @Test("LIB-003: un libro de la nube importado a mano conserva sus metadatos y suma archivo y portada")
    func markImportedKeepsCloudMetadata() async throws {
        let store = try makeStore()
        try await store.addCloudOnly([RemoteBook(id: "a", title: "De la nube", authors: ["Ana"], sizeBytes: 3)])
        try await store.markImported(id: "a", hasCover: true)

        let imported = await store.importedBook(id: "a")
        #expect(imported?.title == "De la nube")
        #expect(imported?.isDownloaded == true)
        #expect(await store.books()[0].coverPath != nil)
    }

    @Test("RDR-002, RDR-014: los ajustes guardados vuelven igual; sin datos, los de fábrica")
    func readerSettingsRoundTrip() throws {
        let defaults = try #require(UserDefaults(suiteName: "ReaderDataTests-\(UUID().uuidString)"))
        let repository = UserDefaultsReaderSettings(defaults: defaults)
        #expect(repository.load() == ReaderSettings())

        let changed = ReaderSettings(theme: .sepia, fontScale: 1.3, font: .mono, lineSpacing: .wide)
        repository.save(changed)
        #expect(repository.load() == changed)
    }

    @Test("Un valor guardado desconocido vuelve al de fábrica, como en Android")
    func unknownStoredValueFallsBack() throws {
        let defaults = try #require(UserDefaults(suiteName: "ReaderDataTests-\(UUID().uuidString)"))
        defaults.set("NEON", forKey: "reader.theme")
        defaults.set(99, forKey: "reader.fontTenths")
        let settings = UserDefaultsReaderSettings(defaults: defaults).load()
        #expect(settings.theme == .light)
        #expect(settings.fontTenths == ReaderSettings.maxTenths)
    }
}

#if os(iOS)
/// Importación con Readium, con el EPUB de `make_fixture_epub.py` (LIB-001 a LIB-004). Solo iOS (ADR 0013).
@MainActor
struct EpubImporterTests {
    private let files = LibraryFiles(root: FileManager.default.temporaryDirectory.appending(path: "EpubImporterTests-\(UUID().uuidString)"))

    private func fixture() throws -> URL {
        try #require(Bundle(for: BundleToken.self).url(forResource: "fixture", withExtension: "epub"))
    }

    private func makeImporter() throws -> (EpubImporter, LibraryStore) {
        let store = LibraryStore(container: try LibraryStore.inMemoryContainer(), files: files)
        return (EpubImporter(store: store, files: files, loader: PublicationLoader()), store)
    }

    @Test("LIB-002, LIB-004: importa el EPUB con su título y autor, y lo guarda por su hash")
    func importsFixture() async throws {
        let (importer, store) = try makeImporter()
        let outcome = await importer.importBook(from: try fixture())

        guard case let .imported(bookId, title) = outcome else {
            Issue.record("Resultado inesperado: \(outcome)")
            return
        }
        #expect(title == "Libro de prueba")
        #expect(bookId.count == 64)
        #expect(await store.isDownloaded(bookId: bookId))
        let book = try #require(await store.books().first)
        #expect(book.author == "Autor de prueba")
    }

    @Test("LIB-003: importar dos veces el mismo archivo no lo duplica")
    func secondImportIsAlreadyInLibrary() async throws {
        let (importer, store) = try makeImporter()
        _ = await importer.importBook(from: try fixture())
        let second = await importer.importBook(from: try fixture())

        guard case .alreadyInLibrary(_, "Libro de prueba") = second else {
            Issue.record("Resultado inesperado: \(second)")
            return
        }
        #expect(await store.books().count == 1)
    }

    @Test("LIB-002: un archivo que no es EPUB se rechaza y no queda en la biblioteca")
    func rejectsGarbage() async throws {
        let (importer, store) = try makeImporter()
        let garbage = files.newTempFile().appendingPathExtension("epub")
        try Data("no soy un libro".utf8).write(to: garbage)

        let outcome = await importer.importBook(from: garbage)

        guard case .rejected = outcome else {
            Issue.record("Resultado inesperado: \(outcome)")
            return
        }
        #expect(await store.books().isEmpty)
        #expect(try FileManager.default.contentsOfDirectory(atPath: files.booksDirectory.path).isEmpty)
    }
}

private final class BundleToken {}
#endif
