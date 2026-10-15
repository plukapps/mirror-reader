import Foundation
import Testing
@testable import ReaderDomain

/// Mismos casos que `NoteSyncTest` de Android (SYN-016, SYN-005, SYN-007, SYN-017).
@Suite("Sincronización de notas")
struct NoteSyncTests {
    private actor Account: AccountRepository {
        func currentUser() async -> AccountUser? { AccountUser(id: "uid", email: nil) }
    }

    /// Base local con las mismas reglas que `LibraryStore`.
    private actor Store: NoteSyncStore {
        struct Row { var note: SyncNote; var syncedAt: Int64? }
        var rows: [String: Row] = [:]
        var booksInCloud: Set<String> = ["libro"]

        func local(_ id: String, editedAt: Int64, syncedAt: Int64? = nil, deletedAt: Int64? = nil, bookId: String = "libro") {
            rows[id] = Row(note: SyncNote(note: makeNote(id, bookId: bookId, updatedAt: editedAt), deletedAt: deletedAt), syncedAt: syncedAt)
        }
        func addBookInCloud(_ id: String) { booksInCloud.insert(id) }
        func row(_ id: String) -> Row? { rows[id] }

        func pendingNotes() async -> [SyncNote] {
            rows.values
                .filter { ($0.syncedAt ?? -1) < $0.note.editedAt && ($0.note.deletedAt != nil || booksInCloud.contains($0.note.note.bookId)) }
                .map(\.note)
                .sorted { $0.note.id < $1.note.id }
        }
        func hasUnsyncedNotes(syncOn: Bool) async -> Bool { rows.values.contains { ($0.syncedAt ?? -1) < $0.note.editedAt } }
        func markNoteSynced(id: String, editedAt: Int64) async {
            guard var row = rows[id], row.note.editedAt == editedAt else { return }
            row.syncedAt = editedAt
            rows[id] = row
        }
        func applyRemote(_ remote: SyncNote) async {
            let current = rows[remote.note.id]
            if remote.deletedAt != nil {
                if let current, current.note.editedAt <= remote.editedAt { rows[remote.note.id] = nil }
                return
            }
            if current == nil || current!.note.editedAt < remote.editedAt {
                rows[remote.note.id] = Row(note: remote, syncedAt: remote.editedAt)
            }
        }
        func purgeSyncedNoteDeletions() async {
            rows = rows.filter { !($0.value.note.deletedAt != nil && ($0.value.syncedAt ?? -1) >= $0.value.note.editedAt) }
        }
    }

    /// La nube, con las reglas de `firestore.rules`: gana la edición más reciente.
    private actor Remote: RemoteNotes {
        var cloud: [String: SyncNote] = [:]
        var offline = false
        var rejected: Set<String> = []
        var pushes = 0
        var listenedSince: [Int64?] = []
        private var continuation: AsyncThrowingStream<RemoteNoteChanges, any Error>.Continuation?

        func setOffline(_ value: Bool) { offline = value }
        func reject(_ bookId: String) { rejected.insert(bookId) }

        func push(_ note: SyncNote) async throws {
            pushes += 1
            if offline { throw RemoteUnavailableError("sin red") }
            if rejected.contains(note.note.bookId) { throw CocoaError(.userCancelled) }
            if let existing = cloud[note.note.id], existing.editedAt > note.editedAt { return }
            cloud[note.note.id] = note
        }

        nonisolated func observeChanges(since: Int64?) -> AsyncThrowingStream<RemoteNoteChanges, any Error> {
            let (stream, continuation) = AsyncThrowingStream<RemoteNoteChanges, any Error>.makeStream()
            Task { await self.keep(continuation, since: since) }
            return stream
        }
        private func keep(_ continuation: AsyncThrowingStream<RemoteNoteChanges, any Error>.Continuation, since: Int64?) {
            self.continuation = continuation
            listenedSince.append(since)
        }
        func emit(_ changes: RemoteNoteChanges) { continuation?.yield(changes) }
    }

    private actor Cursor: NoteSyncCursor {
        var value: Int64?
        func set(_ value: Int64?) { self.value = value }
        func lastNotesSeenAt() async -> Int64? { value }
        func setLastNotesSeenAt(_ value: Int64) async { self.value = value }
    }

    private actor Flags: RemoteFlagSource {
        var on = true
        private var continuation: AsyncStream<Void>.Continuation?
        func set(_ value: Bool) { on = value; continuation?.yield() }
        func setPlan(_ plan: String) async throws {}
        func activate() async throws {}
        func fetchAndActivate() async throws {}
        func value(_ key: String) async -> Bool? { on }
        func plans() async -> [Plan]? { DefaultPlans.all }
        nonisolated func updates() -> AsyncStream<Void> {
            let (stream, continuation) = AsyncStream<Void>.makeStream()
            Task { await self.keep(continuation) }
            return stream
        }
        private func keep(_ c: AsyncStream<Void>.Continuation) { continuation = c }
    }

    private let store = Store()
    private let remote = Remote()
    private let cursor = Cursor()
    private let source = Flags()

    private func makeSync(allowed: Bool = true) async -> (NoteSync, FeatureFlags) {
        let flags = FeatureFlags(source: source)
        await flags.startListening()
        await settle()
        return (NoteSync(remote: remote, store: store, cursor: cursor, flags: flags, account: Account(), allowed: { allowed }), flags)
    }

    private func settle() async {
        for _ in 0..<100 { await Task.yield() }
    }

    @Test("SYN-016: las pendientes suben y quedan marcadas; las rechazadas siguen pendientes")
    func uploadsPending() async {
        await store.local("a", editedAt: 10)
        await store.local("b", editedAt: 20, bookId: "otro")
        await store.addBookInCloud("otro")
        await remote.reject("otro")
        await makeSync().0.flush()
        #expect(await remote.cloud["a"]?.editedAt == 10)
        #expect(await store.pendingNotes().map(\.note.id) == ["b"])
    }

    @Test("SYN-016: una nota de un libro que todavía no está en la nube espera")
    func waitsForTheBook() async {
        await store.local("a", editedAt: 10, bookId: "sin-subir")
        await makeSync().0.flush()
        #expect(await remote.pushes == 0)
    }

    @Test("SYN-001: sin conexión corta y deja todo pendiente")
    func stopsWhenOffline() async {
        await store.local("a", editedAt: 10)
        await store.local("b", editedAt: 11)
        await remote.setOffline(true)
        await makeSync().0.flush()
        #expect(await remote.pushes == 1)
        #expect(await store.pendingNotes().count == 2)
    }

    @Test("SYN-007: un borrado sube y después la nota se elimina de la base")
    func uploadsDeletions() async {
        await store.local("a", editedAt: 30, syncedAt: 10, deletedAt: 30)
        await makeSync().0.flush()
        #expect(await remote.cloud["a"]?.deletedAt == 30)
        #expect(await store.row("a") == nil)
    }

    @Test("SYN-017, PRF-023: apagada o sin la red permitida, no se sube nada")
    func nothingWhenOffOrNotAllowed() async {
        await store.local("a", editedAt: 10)
        await source.set(false)
        await makeSync().0.flush()
        await source.set(true)
        await makeSync(allowed: false).0.flush()
        #expect(await remote.pushes == 0)
    }

    @Test("SYN-016, SYN-005: con la app a la vista llegan las notas de otros dispositivos; gana la edición más reciente")
    func appliesRemoteChanges() async {
        await store.local("mia", editedAt: 50, syncedAt: 50)
        let sync = await makeSync().0
        let reloads = await sync.changes()
        await sync.startListening()
        await settle()
        let nueva = SyncNote(note: makeNote("nueva", updatedAt: 40))
        let vieja = SyncNote(note: makeNote("mia", updatedAt: 20, text: "vieja"))
        await remote.emit(RemoteNoteChanges(notes: [nueva, vieja], newestUpdatedAt: 900))
        var iterator = reloads.makeAsyncIterator()
        _ = await iterator.next()
        #expect(await store.row("nueva") != nil)
        #expect(await store.row("mia")?.note.note.text == "nota mia")
        #expect(await cursor.value == 900)
        await sync.stopListening()
    }

    @Test("SYN-007: un borrado de otro dispositivo quita la nota")
    func appliesRemoteDeletions() async {
        await store.local("a", editedAt: 10, syncedAt: 10)
        let sync = await makeSync().0
        let reloads = await sync.changes()
        await sync.startListening()
        await settle()
        await remote.emit(RemoteNoteChanges(notes: [SyncNote(note: makeNote("a", updatedAt: 30), deletedAt: 30)], newestUpdatedAt: 1000))
        var iterator = reloads.makeAsyncIterator()
        _ = await iterator.next()
        #expect(await store.row("a") == nil)
        await sync.stopListening()
    }

    @Test("SYN-017: apagada no escucha; al prenderse sube lo pendiente y escucha desde la última vez")
    func turningOnUploadsAndListens() async {
        await source.set(false)
        await cursor.set(700)
        await store.local("a", editedAt: 10)
        let sync = await makeSync().0
        await sync.startListening()
        await settle()
        #expect(await remote.listenedSince.isEmpty)
        #expect(await remote.pushes == 0)
        await source.set(true)
        await settle()
        #expect(await remote.cloud["a"]?.editedAt == 10)
        #expect(await remote.listenedSince == [700])
        await sync.stopListening()
    }

    @Test("SYN-017: apagarla con la app a la vista deja de aplicar lo que llega; nunca borra nada")
    func turningOffStopsApplying() async {
        let sync = await makeSync().0
        await sync.startListening()
        await settle()
        await source.set(false)
        await settle()
        await remote.emit(RemoteNoteChanges(notes: [SyncNote(note: makeNote("x", updatedAt: 5))], newestUpdatedAt: 10))
        await settle()
        #expect(await store.row("x") == nil)
        await sync.stopListening()
    }
}

private func makeNote(_ id: String, bookId: String = "libro", updatedAt: Int64, text: String? = nil) -> Note {
    Note(
        id: id, bookId: bookId, locatorJson: "{}", quote: "cita \(id)", text: text ?? "nota \(id)", progression: 0.5,
        createdAt: 1, updatedAt: updatedAt
    )
}
