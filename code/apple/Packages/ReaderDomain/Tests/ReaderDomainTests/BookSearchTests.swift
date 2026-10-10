import Testing
@testable import ReaderDomain

/// Los mismos casos que `BookSearchTest` de Android.
struct BookSearchTests {
    private func book(_ id: String, _ title: String, _ author: String?) -> LibraryBook {
        LibraryBook(id: id, title: title, author: author, coverPath: nil, progressPercent: nil)
    }

    private var library: [LibraryBook] {
        [
            book("med", "Meditaciones", "Marco Aurelio"),
            book("let", "Cartas a Lucilio", "Séneca"),
            book("abo", "Vida de Séneca", "Pierre Grimal"),
            book("noa", "Diario", nil),
        ]
    }

    private func ids(_ query: String, _ scope: SearchScope = .all) -> [String] {
        searchBooks(library, query: query, scope: scope).map(\.id)
    }

    @Test("LIB-013: no distingue mayúsculas ni acentos")
    func ignoresCaseAndAccents() {
        #expect(ids("MEDITACIÓN") == ["med"])
        #expect(ids("aurelio") == ["med"])
        // "Vida de Séneca" lo tiene en el título: va antes que "Cartas a Lucilio", que lo tiene en el autor.
        #expect(ids("seneca") == ["abo", "let"])
    }

    @Test("LIB-013: normaliza el texto")
    func normalizesText() {
        #expect(normalizeForSearch("  Séneca   CARTAS ") == "seneca cartas")
        #expect(normalizeForSearch("Niño") == "nino")
    }

    @Test("LIB-013: cada palabra tiene que estar en el título o en el autor")
    func everyWordMustMatchTitleOrAuthor() {
        #expect(ids("seneca cartas") == ["let"])
        #expect(ids("seneca diario").isEmpty)
    }

    @Test("LIB-015: Autores solo mira el autor")
    func authorsScopeOnlyMatchesTheAuthor() {
        #expect(ids("seneca", .authors) == ["let"])
        #expect(ids("diario", .authors).isEmpty)
    }

    @Test("LIB-015: primero el título que empieza con lo buscado, después el que lo contiene, después solo autor")
    func ordersByTitlePrefixThenTitleThenAuthor() {
        let books = [book("author", "Otro libro", "Ana Vida"), book("contains", "Una vida", nil), book("prefix", "Vida breve", nil)]
        #expect(searchBooks(books, query: "vida").map(\.id) == ["prefix", "contains", "author"])
    }

    @Test("LIB-015: dentro de cada grupo se respeta el orden de la biblioteca")
    func keepsLibraryOrderWithinAGroup() {
        let books = [book("b", "Vida B", nil), book("a", "Vida A", nil)]
        #expect(searchBooks(books, query: "vida").map(\.id) == ["b", "a"])
    }

    @Test("Sin consulta no hay resultados")
    func blankQueryHasNoResults() {
        #expect(searchBooks(library, query: "   ").isEmpty)
    }

    @Test("Sin coincidencias no hay resultados")
    func noMatchesIsEmpty() {
        #expect(searchBooks(library, query: "zzz").isEmpty)
    }
}
