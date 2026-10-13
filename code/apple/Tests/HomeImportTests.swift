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

private let mib: Int64 = 1024 * 1024

private struct PendingBooks: BookUploadRepository {
    let items: [PendingUpload]
    func pending() async throws -> [PendingUpload] { items }
    func markUploaded(bookId: String, sizeBytes: Int64, coverUploaded: Bool) async throws {}
}

private func pendingBook(_ id: String, _ title: String, _ size: Int64) -> PendingUpload {
    PendingUpload(book: RemoteBook(id: id, title: title, authors: [], sizeBytes: size), file: URL(filePath: "/\(id).epub"))
}

private func quotaCheck(used: Int64?, _ items: [PendingUpload], plan: String = "library") -> ImportQuotaCheck {
    ImportQuotaCheck(
        uploads: PendingBooks(items: items),
        cache: PlansMemoryQuotaCache(used.map { StorageQuota(usedBytes: $0, quotaBytes: 15 * mib, plan: plan) })
    )
}

@MainActor
struct HomeImportTests {
    @Test("PLN-008: si el libro importado no entra, se importa igual y aparece la hoja; Ahora no la cierra")
    func importedBookDoesNotFit() async {
        let check = quotaCheck(used: 12 * mib, [pendingBook("a", "Walden", 8 * mib)])
        let viewModel = HomeViewModel(library: CountingLibrary(), importer: importer, quotaCheck: check)
        await viewModel.importBooks([file("a.epub")])
        #expect(viewModel.messages == ["Se importó 1 libro."])
        #expect(viewModel.quotaBlock?.title == "Walden")
        #expect(viewModel.quotaBlock?.block == .doesNotFit(bookBytes: 8 * mib, freeBytes: 3 * mib))
        viewModel.dismissQuotaBlock()
        #expect(viewModel.quotaBlock == nil)
    }

    @Test("PLN-014: en Gratis un libro de 3 MB se importa igual y aparece la hoja de archivo muy grande")
    func importedBookTooLarge() async {
        let check = quotaCheck(used: 0, [pendingBook("a", "Walden", 3 * mib)], plan: "free")
        let viewModel = HomeViewModel(library: CountingLibrary(), importer: importer, quotaCheck: check)
        await viewModel.importBooks([file("a.epub")])
        #expect(viewModel.messages == ["Se importó 1 libro."])
        #expect(viewModel.quotaBlock?.block == .tooLarge(bookBytes: 3 * mib, maxBytes: 2 * mib))
    }

    @Test("PLN-007: con el espacio lleno aparece la hoja de espacio lleno")
    func importedWithFullSpace() async {
        let check = quotaCheck(used: 15 * mib, [pendingBook("a", "Walden", mib)])
        let viewModel = HomeViewModel(library: CountingLibrary(), importer: importer, quotaCheck: check)
        await viewModel.importBooks([file("a.epub")])
        #expect(viewModel.quotaBlock?.block == .full)
    }

    @Test("PLN-009: sin cuota conocida, o si el libro ya estaba, no hay hoja")
    func noSheet() async {
        let unknown = HomeViewModel(
            library: CountingLibrary(), importer: importer, quotaCheck: quotaCheck(used: nil, [pendingBook("a", "Walden", 99 * mib)])
        )
        await unknown.importBooks([file("a.epub")])
        #expect(unknown.quotaBlock == nil)
        let repeated = HomeViewModel(
            library: CountingLibrary(), importer: importer, quotaCheck: quotaCheck(used: 15 * mib, [pendingBook("c", "Meditaciones", mib)])
        )
        await repeated.importBooks([file("c.epub")])
        #expect(repeated.quotaBlock == nil)
    }

    @Test("SYN-001: importar pide una sincronización para subir lo importado")
    func importRequestsSync() async {
        var requests = 0
        let viewModel = HomeViewModel(library: CountingLibrary(), importer: importer) { requests += 1 }
        await viewModel.importBooks([file("a.epub")])
        #expect(requests == 1)
        await viewModel.openIncoming(file("b.epub"))
        #expect(requests == 2)
    }

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
