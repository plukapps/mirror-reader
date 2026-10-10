import Foundation
import ReaderDomain
import Testing
@testable import Reader

// Lado local de la sincronización de la posición (plan `2026-10-10-ios-sync.md`, K-152), como
// `ReadingPositionDao` y `SyncPreferencesImpl` de Android.
struct PositionDataTests {
    private let files = LibraryFiles(root: FileManager.default.temporaryDirectory.appending(path: "PositionDataTests-\(UUID().uuidString)"))
    private let clock = TestClock()

    private func makeStore() throws -> LibraryStore {
        let clock = clock
        return LibraryStore(container: try LibraryStore.inMemoryContainer(), files: files, now: { clock.now })
    }

    private var nowMillis: Int64 { Int64((clock.now.timeIntervalSince1970 * 1000).rounded()) }

    private func cloudBook(_ store: LibraryStore, _ id: String) async throws {
        try await store.addCloudOnly([RemoteBook(id: id, title: id, authors: [], sizeBytes: 1)])
    }

    @Test("SYN-011: leer guarda la posición como pendiente; solo sale si el libro ya está en la nube")
    func pendingNeedsBookInCloud() async throws {
        let store = try makeStore()
        try await cloudBook(store, "nube")
        try await store.addImported(id: "local", title: "L", author: nil, hasCover: false, sizeBytes: 1)
        await store.save(bookId: "nube", position: SavedPosition(locatorJson: "n", progress: 0.3))
        await store.save(bookId: "local", position: SavedPosition(locatorJson: "l", progress: 0.4))

        #expect(await store.pendingPositions() == [ReadingPosition(bookId: "nube", locatorJson: "n", progress: 0.3, readAt: nowMillis)])
        #expect(await store.local(bookId: "local")?.isSynced == false)
    }

    @Test("SYN-011: marcar enviada solo vale para la lectura que se envió")
    func markSyncedOnlySameReading() async throws {
        let store = try makeStore()
        try await cloudBook(store, "a")
        await store.save(bookId: "a", position: SavedPosition(locatorJson: "1", progress: 0.1))
        let sent = nowMillis
        clock.advance()
        await store.save(bookId: "a", position: SavedPosition(locatorJson: "2", progress: 0.2))

        await store.markSynced(bookId: "a", readAt: sent)
        #expect(await store.local(bookId: "a")?.isSynced == false)

        await store.markSynced(bookId: "a", readAt: nowMillis)
        #expect(await store.local(bookId: "a")?.isSynced == true)
        #expect(await store.pendingPositions().isEmpty)

        await store.markPending(bookId: "a")
        #expect(await store.local(bookId: "a")?.isSynced == false)
    }

    @Test("SYN-003: la posición de la nube se guarda como enviada solo si es más reciente")
    func applyRemoteOnlyIfNewer() async throws {
        let store = try makeStore()
        try await cloudBook(store, "a")
        await store.applyRemote(ReadingPosition(bookId: "a", locatorJson: "nube", progress: 0.5, readAt: 10))
        #expect(await store.local(bookId: "a") == LocalPosition(
            position: ReadingPosition(bookId: "a", locatorJson: "nube", progress: 0.5, readAt: 10), isSynced: true
        ))

        await store.applyRemote(ReadingPosition(bookId: "a", locatorJson: "vieja", progress: 0.1, readAt: 5))
        #expect(await store.local(bookId: "a")?.position.locatorJson == "nube")
    }

    @Test("SYN-011: el identificador de la instalación se genera una vez y el último instante visto se guarda")
    func preferences() async throws {
        let defaults = try #require(UserDefaults(suiteName: "PositionDataTests-\(UUID().uuidString)"))
        let prefs = UserDefaultsSyncPreferences(defaults: defaults, deviceName: "iPhone")
        let id = await prefs.deviceId()
        #expect(!id.isEmpty)
        #expect(await UserDefaultsSyncPreferences(defaults: defaults, deviceName: "iPhone").deviceId() == id)
        #expect(prefs.deviceName() == "iPhone")

        #expect(await prefs.lastPositionsSeenAt() == nil)
        await prefs.setLastPositionsSeenAt(1_234)
        #expect(await prefs.lastPositionsSeenAt() == 1_234)
    }

    @Test("SYN-011: el nombre del dispositivo cabe en lo que acepta la nube")
    func deviceNameIsTrimmed() {
        let prefs = UserDefaultsSyncPreferences(defaults: .standard, deviceName: String(repeating: "x", count: 100))
        #expect(prefs.deviceName().count == 64)
        #expect(!UserDefaultsSyncPreferences(defaults: .standard, deviceName: " ").deviceName().isEmpty)
    }
}
