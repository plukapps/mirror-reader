import Foundation
import Observation
import ReaderDomain

/// Búsqueda local en la biblioteca (LIB-006, LIB-013, LIB-015), como `SearchViewModel` de Android.
/// `RootView` lo crea una vez: la consulta y el filtro siguen ahí al cambiar de pestaña.
@MainActor
@Observable
final class SearchViewModel {
    /// Texto del campo. Los resultados se recalculan al escribir (LIB-013).
    var query = "" { didSet { update() } }
    var scope: SearchScope = .all { didSet { update() } }
    private(set) var loading = true
    /// Libros que coinciden, en el orden de LIB-015.
    private(set) var results: [LibraryBook] = []
    /// Hay algo escrito: se muestran resultados (o que no hay) en lugar de la ayuda.
    var hasQuery: Bool { !query.trimmingCharacters(in: .whitespaces).isEmpty }

    /// El campo ya tomó el foco al entrar por primera vez.
    var autoFocused = false

    private let library: LibraryRepository
    private var books: [LibraryBook] = []

    init(library: LibraryRepository) {
        self.library = library
    }

    /// Lee la biblioteca. Se llama cada vez que aparece la pantalla, para ver lo que trajo la sincronización.
    func load() async {
        books = await library.books()
        loading = false
        update()
    }

    func clear() { query = "" }

    private func update() {
        results = searchBooks(books, query: query, scope: scope)
    }
}
