import Foundation
import ReaderDomain
import Testing
@testable import Reader

// Detalle del libro con sus notas (LIB-022, ANN-010), sobre la base local en memoria.
@MainActor
struct BookDetailViewModelTests {
    private let files = LibraryFiles(root: FileManager.default.temporaryDirectory.appending(path: "BookDetailTests-\(UUID().uuidString)"))

    private func note(_ id: String, bookId: String = "libro", progression: Double) -> Note {
        Note(id: id, bookId: bookId, locatorJson: "{}", quote: "cita \(id)", text: "nota \(id)", progression: progression, createdAt: 1, updatedAt: 1)
    }

    private func makeStore() async throws -> LibraryStore {
        let store = LibraryStore(container: try LibraryStore.inMemoryContainer(), files: files)
        try await store.addCloudOnly([RemoteBook(id: "libro", title: "Meditaciones", authors: ["Marco Aurelio"], sizeBytes: 1)])
        return store
    }

    @Test("LIB-022, ANN-010: el libro y sus notas en el orden del libro; las de otros libros no aparecen")
    func showsTheBookAndItsNotes() async throws {
        let store = try await makeStore()
        await store.save(note("tarde", progression: 0.8))
        await store.save(note("ajena", bookId: "otro", progression: 0.1))
        await store.save(note("temprano", progression: 0.2))
        let viewModel = BookDetailViewModel(bookId: "libro", library: store, notes: store)
        await viewModel.load()
        guard case let .ready(book, notes) = viewModel.phase else { Issue.record("no cargó"); return }
        #expect(book.title == "Meditaciones")
        #expect(book.author == "Marco Aurelio")
        #expect(notes.map(\.id) == ["temprano", "tarde"])
    }

    @Test("ANN-010: una nota nueva aparece al volver a cargar (al cerrar el lector)")
    func reloadShowsNewNotes() async throws {
        let store = try await makeStore()
        let viewModel = BookDetailViewModel(bookId: "libro", library: store, notes: store)
        await viewModel.load()
        await store.save(note("nueva", progression: 0.5))
        await viewModel.load()
        guard case let .ready(_, notes) = viewModel.phase else { Issue.record("no cargó"); return }
        #expect(notes.map(\.id) == ["nueva"])
    }

    @Test("LIB-022: si el libro ya no está en la biblioteca, el detalle se cierra")
    func removedBookIsGone() async throws {
        let store = try await makeStore()
        await store.remove(bookId: "libro")
        let viewModel = BookDetailViewModel(bookId: "libro", library: store, notes: store)
        await viewModel.load()
        #expect(viewModel.phase == .gone)
    }
}
