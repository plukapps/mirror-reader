import Foundation
import ReaderDomain
import Testing
@testable import Reader

// Importar y abrir libros desde Inicio (LIB-001, LIB-007), como `ImportBooksUseCase`, `toMessages` e
// `importIncoming` de Android (plan `2026-10-09-ios-reader.md`, K-131).

private actor CountingLibrary: LibraryRepository {
    private(set) var reads = 0
    func books() async -> [LibraryBook] {
        reads += 1
        return []
    }
}

private struct FakeImporter: BookImporter {
    let outcomes: [String: ImportOutcome]
    func importBook(from url: URL) async -> ImportOutcome {
        outcomes[url.lastPathComponent] ?? .rejected(message: "No se pudo leer el archivo.")
    }
}

private let importer = FakeImporter(outcomes: [
    "a.epub": .imported(bookId: "a", title: "Walden"),
    "b.epub": .imported(bookId: "b", title: "Dejar ir"),
    "c.epub": .alreadyInLibrary(bookId: "c", title: "Meditaciones"),
    "d.epub": .rejected(message: "No se pudo abrir el libro. El archivo puede estar dañado o protegido con DRM."),
])

private func file(_ name: String) -> URL { URL(fileURLWithPath: "/tmp/\(name)") }

@MainActor
struct HomeImportTests {
    @Test("LIB-001: importar varios cuenta los nuevos y avisa los repetidos y los rechazados, y recarga Inicio")
    func importSummarizesOutcomes() async {
        let library = CountingLibrary()
        let viewModel = HomeViewModel(library: library, importer: importer)

        await viewModel.importBooks([file("a.epub"), file("b.epub"), file("c.epub"), file("d.epub")])

        #expect(viewModel.messages == [
            "Se importaron 2 libros.",
            "«Meditaciones» ya está en tu biblioteca.",
            "No se pudo abrir el libro. El archivo puede estar dañado o protegido con DRM.",
        ])
        #expect(await library.reads == 1)
    }

    @Test("LIB-001: un solo libro nuevo se cuenta en singular")
    func singleImportIsSingular() async {
        let viewModel = HomeViewModel(library: CountingLibrary(), importer: importer)
        await viewModel.importBooks([file("a.epub")])
        #expect(viewModel.messages == ["Se importó 1 libro."])
    }

    @Test("LIB-001: \"Abrir con\" importa y abre el libro, aunque ya estuviera en la biblioteca")
    func openWithImportsAndOpens() async {
        let viewModel = HomeViewModel(library: CountingLibrary(), importer: importer)

        await viewModel.openIncoming(file("c.epub"))
        #expect(viewModel.readerBookId == "c")

        viewModel.closeReader()
        await viewModel.openIncoming(file("d.epub"))
        #expect(viewModel.readerBookId == nil)
        #expect(viewModel.messages.last == "No se pudo abrir el libro. El archivo puede estar dañado o protegido con DRM.")
    }

    @Test("RDR-007: tocar un libro abre el lector; al cerrarlo Inicio se recarga con el progreso nuevo")
    func openingAndClosingTheReader() async {
        let library = CountingLibrary()
        let viewModel = HomeViewModel(library: library, importer: importer)

        viewModel.open(bookId: "a")
        #expect(viewModel.readerBookId == "a")

        viewModel.closeReader()
        #expect(viewModel.readerBookId == nil)
        await viewModel.reload()
        #expect(await library.reads == 1)
    }

    @Test("Sin lector en esta plataforma (Mac, ADR 0013), tocar un libro avisa en lugar de abrir")
    func withoutReaderShowsNotice() {
        let viewModel = HomeViewModel(library: CountingLibrary())
        #expect(!viewModel.canImport)

        viewModel.open(bookId: "a")

        #expect(viewModel.readerBookId == nil)
        #expect(viewModel.messages == ["El lector llega más adelante a la Mac."])
    }

    @Test("Los avisos se descartan de a uno")
    func messagesAreConsumed() async {
        let viewModel = HomeViewModel(library: CountingLibrary(), importer: importer)
        await viewModel.importBooks([file("a.epub"), file("c.epub")])
        viewModel.dismissMessage()
        #expect(viewModel.messages == ["«Meditaciones» ya está en tu biblioteca."])
    }
}
