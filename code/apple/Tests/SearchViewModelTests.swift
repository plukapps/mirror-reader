import Foundation
import ReaderDomain
import Testing
@testable import Reader

/// Biblioteca que se puede cambiar entre lecturas, como la que deja la sincronización.
private actor MutableLibrary: LibraryRepository {
    private var stored: [LibraryBook]
    init(_ books: [LibraryBook]) { stored = books }
    func books() async -> [LibraryBook] { stored }
    func replace(_ books: [LibraryBook]) { stored = books }
}

private func book(_ id: String, _ title: String, _ author: String?, progress: Int? = nil) -> LibraryBook {
    LibraryBook(id: id, title: title, author: author, coverPath: nil, progressPercent: progress)
}

private let letters = book("let", "Cartas a Lucilio", "Séneca")
private let about = book("abo", "Vida de Séneca", "Pierre Grimal")
private let meditations = book("med", "Meditaciones", "Marco Aurelio")

@MainActor
struct SearchViewModelTests {
    @Test("LIB-013: sin consulta no hay resultados")
    func startsWithoutResults() async {
        let viewModel = SearchViewModel(library: MutableLibrary([letters, about]))
        #expect(viewModel.loading)
        await viewModel.load()
        #expect(!viewModel.loading)
        #expect(!viewModel.hasQuery)
        #expect(viewModel.results.isEmpty)
    }

    @Test("LIB-013: los resultados aparecen mientras escribo")
    func typingShowsResults() async {
        let viewModel = SearchViewModel(library: MutableLibrary([meditations, letters, about]))
        await viewModel.load()

        viewModel.query = "séneca"
        #expect(viewModel.hasQuery)
        #expect(viewModel.results.map(\.id) == ["abo", "let"])

        viewModel.query = "seneca cartas"
        #expect(viewModel.results.map(\.id) == ["let"])
    }

    @Test("LIB-015: el filtro Autores deja solo los que coinciden por autor")
    func authorsScopeFilters() async {
        let viewModel = SearchViewModel(library: MutableLibrary([letters, about]))
        await viewModel.load()
        viewModel.query = "seneca"

        viewModel.scope = .authors
        #expect(viewModel.results.map(\.id) == ["let"])

        viewModel.scope = .all
        #expect(viewModel.results.map(\.id) == ["abo", "let"])
    }

    @Test("LIB-013: al volver a la pantalla ve los cambios de la biblioteca")
    func reloadSeesLibraryChanges() async {
        let library = MutableLibrary([letters])
        let viewModel = SearchViewModel(library: library)
        await viewModel.load()
        viewModel.query = "lucilio"
        #expect(viewModel.results.first?.progressPercent == nil)

        await library.replace([book("let", "Cartas a Lucilio", "Séneca", progress: 42)])
        await viewModel.load()

        #expect(viewModel.results.first?.progressPercent == 42)
    }

    @Test("Borrar la búsqueda vacía la consulta y los resultados")
    func clearEmptiesQuery() async {
        let viewModel = SearchViewModel(library: MutableLibrary([letters]))
        await viewModel.load()
        viewModel.query = "cartas"
        viewModel.clear()
        #expect(viewModel.query.isEmpty)
        #expect(viewModel.results.isEmpty)
    }
}

struct SearchFormattingTests {
    @Test("LIB-014: el estado dice el progreso, nuevo, terminado y si está solo en la nube")
    func statusText() {
        #expect(searchStatus(book("a", "A", nil, progress: 42)) == "Leyendo · 42 %")
        #expect(searchStatus(book("a", "A", nil)) == "Nuevo")
        #expect(searchStatus(book("a", "A", nil, progress: 100)) == "Terminado")
        let cloud = LibraryBook(id: "c", title: "C", author: nil, coverPath: nil, progressPercent: nil, isDownloaded: false)
        #expect(searchStatus(cloud) == "Nuevo · En la nube")
    }

    @Test("LIB-014: la cantidad de resultados, en singular y plural")
    func resultsCount() {
        #expect(searchResultsCount(1) == "1 resultado")
        #expect(searchResultsCount(3) == "3 resultados")
    }
}
