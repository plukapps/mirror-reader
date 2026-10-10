import Foundation
import Observation
import ReaderDomain

/// Estado de la biblioteca (LIB-010, LIB-011), como `LibraryViewModel` de Android.
@MainActor
@Observable
final class LibraryViewModel {
    private(set) var loading = true
    /// Filtro activo. Inicio puede elegirlo antes de abrir la pantalla (HOM-011).
    private(set) var filter: LibraryFilter = .all
    private var allBooks: [LibraryBook] = []

    private let library: LibraryRepository

    init(library: LibraryRepository) {
        self.library = library
    }

    /// Libros que pasan el filtro activo, con el importado más reciente primero.
    var books: [LibraryBook] { allBooks.filter(by: filter) }

    /// Cantidad de libros de cada pestaña, sin importar el filtro activo.
    func count(_ filter: LibraryFilter) -> Int { allBooks.count(by: filter) }

    /// No hay ningún libro, sin importar el filtro.
    var isLibraryEmpty: Bool { !loading && allBooks.isEmpty }

    func select(_ filter: LibraryFilter) {
        self.filter = filter
    }

    /// Lee la base local. Se llama al abrir la pantalla y cada vez que la sincronización la cambia (SYN-001).
    func load() async {
        allBooks = await library.books()
        loading = false
    }
}
