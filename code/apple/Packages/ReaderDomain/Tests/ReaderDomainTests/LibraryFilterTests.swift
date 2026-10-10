import Testing
@testable import ReaderDomain

// Mismos casos que el filtro de la biblioteca en Android: el spec es el contrato común (ADR 0001).
struct LibraryFilterTests {
    private func book(_ id: String, _ progress: Int?) -> LibraryBook {
        LibraryBook(id: id, title: "T\(id)", author: nil, coverPath: nil, progressPercent: progress)
    }

    private var books: [LibraryBook] {
        [book("nuevo", nil), book("leyendo", 42), book("empezado", 0), book("terminado", 100)]
    }

    @Test("LIB-010: Todos muestra todos los libros en el mismo orden")
    func allKeepsEveryBook() {
        #expect(books.filter(by: .all).map(\.id) == ["nuevo", "leyendo", "empezado", "terminado"])
    }

    @Test("LIB-010: un libro al 42 % está en Leyendo y no en Terminados")
    func readingAndFinished() {
        #expect(books.filter(by: .reading).map(\.id) == ["leyendo", "empezado"])
        #expect(books.filter(by: .finished).map(\.id) == ["terminado"])
    }

    @Test("LIB-010: un libro sin abrir no está en Leyendo ni en Terminados")
    func newBookIsInNeither() {
        let fresh = [book("nuevo", nil)]
        #expect(fresh.filter(by: .reading).isEmpty)
        #expect(fresh.filter(by: .finished).isEmpty)
    }

    @Test("LIB-010: cantidades de las pestañas")
    func counts() {
        #expect(books.count(by: .all) == 4)
        #expect(books.count(by: .reading) == 2)
        #expect(books.count(by: .finished) == 1)
    }

    @Test("LIB-007: un libro está descargado salvo que se diga lo contrario")
    func downloadedByDefault() {
        #expect(book("a", nil).isDownloaded)
        #expect(!LibraryBook(id: "b", title: "B", author: nil, coverPath: nil, progressPercent: nil, isDownloaded: false).isDownloaded)
    }
}
