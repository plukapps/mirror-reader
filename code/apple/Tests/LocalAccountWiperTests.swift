import Foundation
import ReaderDomain
import Testing
@testable import Reader

// PRF-009 con SwiftData en memoria, carpetas temporales y un `UserDefaults` propio: nunca toca los datos de la app.
struct LocalAccountWiperTests {
    private let files = LibraryFiles(root: FileManager.default.temporaryDirectory.appending(path: "Wiper-\(UUID().uuidString)"))
    private let suite = "LocalAccountWiperTests-\(UUID().uuidString)"

    @Test("PRF-009: borra libros, archivos y preferencias de la cuenta; quedan los ajustes del lector y el dispositivo")
    func wipesAccountData() async throws {
        let defaults = try #require(UserDefaults(suiteName: suite))
        defer { defaults.removePersistentDomain(forName: suite) }
        let store = LibraryStore(container: try LibraryStore.inMemoryContainer(), files: files)
        try await store.addCloudOnly([RemoteBook(id: "b1", title: "Drácula", authors: [], sizeBytes: 7)])
        try FileManager.default.createDirectory(at: files.booksDirectory, withIntermediateDirectories: true)
        try Data("epub".utf8).write(to: files.bookFile(bookId: "b1"))
        defaults.set("SEPIA", forKey: "reader.theme")
        defaults.set("d1", forKey: "sync.deviceId")
        defaults.set(5, forKey: "sync.lastPositionsSeenAt")
        defaults.set(20, forKey: "readingGoalMinutes")
        UserDefaultsQuotaCache(defaults: defaults).save(StorageQuota(usedBytes: 1, quotaBytes: 2))
        UserDefaultsUsageNotice(defaults: defaults).dismiss()

        try await LocalAccountWiper(store: store, files: files, defaults: defaults).wipe()

        #expect(await store.books().isEmpty)
        #expect(!FileManager.default.fileExists(atPath: files.bookFile(bookId: "b1").path(percentEncoded: false)))
        #expect(defaults.string(forKey: "reader.theme") == "SEPIA")
        #expect(defaults.string(forKey: "sync.deviceId") == "d1")
        #expect(defaults.object(forKey: "sync.lastPositionsSeenAt") == nil)
        #expect(defaults.object(forKey: "readingGoalMinutes") == nil)
        #expect(UserDefaultsQuotaCache(defaults: defaults).last() == nil)
        #expect(!UserDefaultsUsageNotice(defaults: defaults).isDismissed())
    }

    @Test("PRF-009: sin nada guardado, borrar no falla")
    func wipesEmpty() async throws {
        let defaults = try #require(UserDefaults(suiteName: suite))
        defer { defaults.removePersistentDomain(forName: suite) }
        let store = LibraryStore(container: try LibraryStore.inMemoryContainer(), files: files)
        try await LocalAccountWiper(store: store, files: files, defaults: defaults).wipe()
    }
}
