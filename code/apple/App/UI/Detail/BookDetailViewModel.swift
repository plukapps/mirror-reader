import Foundation
import Observation
import ReaderDomain

/// Detalle de un libro (LIB-022), como `BookDetailViewModel` de Android: el libro y sus notas en el orden del libro
/// (ANN-010). Se vuelve a leer al aparecer y al cerrar el lector.
@MainActor
@Observable
final class BookDetailViewModel {
    enum Phase: Equatable {
        case loading
        case ready(LibraryBook, [Note])
        /// El libro ya no está en la biblioteca (se quitó, también desde otro dispositivo).
        case gone
    }

    let bookId: String
    private(set) var phase: Phase = .loading
    private let library: LibraryRepository
    private let notes: NoteRepository

    init(bookId: String, library: LibraryRepository, notes: NoteRepository) {
        self.bookId = bookId
        self.library = library
        self.notes = notes
    }

    func load() async {
        guard let book = await library.books().first(where: { $0.id == bookId }) else {
            phase = .gone
            return
        }
        phase = .ready(book, await notes.notes(bookId: bookId))
    }
}
