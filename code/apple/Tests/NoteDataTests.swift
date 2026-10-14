import Foundation
import ReaderDomain
import Testing
@testable import Reader

// Notas en la base local con SwiftData en memoria (ANN-003, ADR 0012).
struct NoteDataTests {
    private let files = LibraryFiles(root: FileManager.default.temporaryDirectory.appending(path: "NoteDataTests-\(UUID().uuidString)"))

    private func makeStore() throws -> LibraryStore {
        LibraryStore(container: try LibraryStore.inMemoryContainer(), files: files)
    }

    private func note(_ id: String, bookId: String = "libro", progression: Double = 0.5, createdAt: Int64 = 1) -> Note {
        Note(
            id: id, bookId: bookId, locatorJson: #"{"href":"c.xhtml"}"#, quote: "texto citado \(id)", text: "nota \(id)",
            progression: progression, createdAt: createdAt, updatedAt: createdAt
        )
    }

    @Test("ANN-006: la nota guarda el lugar y el texto citado")
    func roundTrips() async throws {
        let store = try makeStore()
        await store.save(note("n1"))
        #expect(await store.note(id: "n1") == note("n1"))
    }

    @Test("ANN-010: por libro y en el orden del libro; a igual lugar, la más vieja primero")
    func bookOrder() async throws {
        let store = try makeStore()
        await store.save(note("tarde", progression: 0.8))
        await store.save(note("otro", bookId: "otro-libro", progression: 0.1))
        await store.save(note("temprano", progression: 0.2))
        await store.save(note("igual-nueva", progression: 0.8, createdAt: 5))
        #expect(await store.notes(bookId: "libro").map(\.id) == ["temprano", "tarde", "igual-nueva"])
    }

    @Test("ANN-005: editar cambia el texto y conserva lo demás; borrar la quita")
    func editsAndDeletes() async throws {
        let store = try makeStore()
        await store.save(note("n1"))
        var edited = note("n1")
        edited.text = "editada"
        edited.updatedAt = 9
        await store.save(edited)
        #expect(await store.note(id: "n1") == edited)
        await store.delete(id: "n1")
        #expect(await store.note(id: "n1") == nil)
    }

    @Test("ANN-011: quitar el libro borra sus notas y deja las de los demás")
    func removingABookDeletesItsNotes() async throws {
        let store = try makeStore()
        try await store.addCloudOnly([RemoteBook(id: "libro", title: "T", authors: [], sizeBytes: 1)])
        await store.save(note("a"))
        await store.save(note("b", bookId: "otro-libro"))
        await store.remove(bookId: "libro")
        #expect(await store.notes(bookId: "libro").isEmpty)
        #expect(await store.notes(bookId: "otro-libro").map(\.id) == ["b"])
    }

    @Test("PRF-009: cerrar sesión borra las notas")
    func deleteAllRemovesNotes() async throws {
        let store = try makeStore()
        await store.save(note("a"))
        try await store.deleteAll()
        #expect(await store.notes(bookId: "libro").isEmpty)
    }

    // MARK: Sincronizar (SYN-016, SYN-005, SYN-007)

    private func withUpdated(_ note: Note, _ updatedAt: Int64, text: String? = nil) -> Note {
        var copy = note
        copy.updatedAt = updatedAt
        if let text { copy.text = text }
        return copy
    }

    @Test("SYN-007: borrar una nota que la nube nunca tuvo la elimina; una subida queda marcada y pendiente")
    func deletingMarksOnlyNotesTheCloudHas() async throws {
        let store = try makeStore()
        try await store.addCloudOnly([RemoteBook(id: "libro", title: "T", authors: [], sizeBytes: 1)])
        await store.save(note("local"))
        await store.delete(id: "local")
        #expect(await store.pendingNotes().isEmpty)
        await store.save(note("subida"))
        await store.markNoteSynced(id: "subida", editedAt: 1)
        await store.delete(id: "subida")
        #expect(await store.note(id: "subida") == nil)
        let pending = await store.pendingNotes()
        #expect(pending.map(\.note.id) == ["subida"])
        #expect(pending.first?.deletedAt != nil)
    }

    @Test("SYN-016: pendientes solo si el libro está en la nube; subir las marca enviadas")
    func pendingNeedsTheBookInTheCloud() async throws {
        let store = try makeStore()
        try await store.addCloudOnly([RemoteBook(id: "libro", title: "T", authors: [], sizeBytes: 1)])
        await store.save(note("en-la-nube"))
        await store.save(note("sin-libro", bookId: "otro-libro"))
        #expect(await store.pendingNotes().map(\.note.id) == ["en-la-nube"])
        await store.markNoteSynced(id: "en-la-nube", editedAt: 1)
        #expect(await store.pendingNotes().isEmpty)
        #expect(await store.hasUnsyncedNotes(syncOn: true)) // "sin-libro" todavía no subió
    }

    @Test("SYN-005: lo de la nube se aplica solo si es más reciente; una edición local más nueva no se pisa")
    func appliesOnlyNewerRemoteNotes() async throws {
        let store = try makeStore()
        await store.save(withUpdated(note("n1"), 10, text: "local"))
        await store.applyRemote(SyncNote(note: withUpdated(note("n1"), 5, text: "vieja")))
        #expect(await store.note(id: "n1")?.text == "local")
        await store.applyRemote(SyncNote(note: withUpdated(note("n1"), 20, text: "nueva")))
        #expect(await store.note(id: "n1")?.text == "nueva")
        #expect(await store.hasUnsyncedNotes(syncOn: true) == false) // vino de la nube: ya está allí
        await store.applyRemote(SyncNote(note: withUpdated(note("n2"), 3)))
        #expect(await store.note(id: "n2")?.text == "nota n2")
    }

    @Test("SYN-007: un borrado de la nube más reciente elimina la nota; uno más viejo que una edición local, no")
    func appliesRemoteDeletions() async throws {
        let store = try makeStore()
        await store.save(withUpdated(note("n1"), 10))
        await store.applyRemote(SyncNote(note: withUpdated(note("n1"), 5), deletedAt: 5))
        #expect(await store.note(id: "n1") != nil)
        await store.applyRemote(SyncNote(note: withUpdated(note("n1"), 30), deletedAt: 30))
        #expect(await store.note(id: "n1") == nil)
        #expect(await store.pendingNotes().isEmpty)
    }

    @Test("ANN-011, SYN-007: al quitar el libro, sus notas subidas quedan marcadas para borrar en la nube")
    func removingABookMarksSyncedNotes() async throws {
        let store = try makeStore()
        try await store.addCloudOnly([RemoteBook(id: "libro", title: "T", authors: [], sizeBytes: 1)])
        await store.save(note("subida"))
        await store.markNoteSynced(id: "subida", editedAt: 1)
        await store.save(note("local"))
        await store.remove(bookId: "libro")
        #expect(await store.notes(bookId: "libro").isEmpty)
        let pending = await store.pendingNotes()
        #expect(pending.map(\.note.id) == ["subida"])
        #expect(pending.first?.deletedAt != nil)
        await store.markNoteSynced(id: "subida", editedAt: pending[0].editedAt)
        await store.purgeSyncedNoteDeletions()
        #expect(await store.pendingNotes().isEmpty)
        #expect(await store.hasUnsyncedNotes(syncOn: false) == false)
    }

    @Test("PRF-009: con la sincronización apagada, cualquier nota viva se perdería al cerrar sesión")
    func unsyncedDependsOnTheSwitch() async throws {
        let store = try makeStore()
        await store.save(note("n1"))
        await store.markNoteSynced(id: "n1", editedAt: 1)
        #expect(await store.hasUnsyncedNotes(syncOn: true) == false)
        #expect(await store.hasUnsyncedNotes(syncOn: false))
    }
}
