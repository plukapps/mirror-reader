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
}
