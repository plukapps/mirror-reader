import Foundation
import ReaderDomain
import Testing
@testable import Reader

/// Biblioteca que se puede cambiar entre lecturas, como la base cuando sincroniza.
private actor MutableLibrary: LibraryRepository {
    var stored: [LibraryBook]
    init(_ stored: [LibraryBook]) { self.stored = stored }
    func books() async -> [LibraryBook] { stored }
    func add(_ book: LibraryBook) { stored.append(book) }
}

private func book(_ id: String, _ progress: Int?) -> LibraryBook {
    LibraryBook(id: id, title: "T\(id)", author: nil, coverPath: nil, progressPercent: progress)
}

private final class MemoryUsageNotice: UsageNoticePreference, @unchecked Sendable {
    var dismissed = false
    func isDismissed() -> Bool { dismissed }
    func dismiss() { dismissed = true }
}

private let mib: Int64 = 1024 * 1024

@MainActor
struct LibraryViewModelTests {
    @Test("PLN-011: con 12 MB de 15 MB aparece el aviso; cerrarlo lo descarta y no vuelve")
    func usageNotice() async {
        let cache = PlansMemoryQuotaCache(StorageQuota(usedBytes: 12 * mib, quotaBytes: 15 * mib))
        let preference = MemoryUsageNotice()
        let viewModel = LibraryViewModel(library: MutableLibrary([]), quotaCache: cache, usageNotice: preference)
        await viewModel.load()
        #expect(viewModel.usageNotice?.text == "Usás 12 MB de 15 MB")
        #expect(viewModel.usageNotice?.free == "3 MB")
        viewModel.dismissUsageNotice()
        #expect(viewModel.usageNotice == nil)
        #expect(preference.dismissed)
        await viewModel.load()
        #expect(viewModel.usageNotice == nil)
    }

    @Test("PLN-011: la cuota que trae una pasada se ve al recargar; debajo del 80 % no hay aviso")
    func usageNoticeFollowsCache() async {
        let cache = PlansMemoryQuotaCache(StorageQuota(usedBytes: 2 * mib, quotaBytes: 15 * mib))
        let viewModel = LibraryViewModel(library: MutableLibrary([]), quotaCache: cache, usageNotice: MemoryUsageNotice())
        await viewModel.load()
        #expect(viewModel.usageNotice == nil)
        cache.saved = StorageQuota(usedBytes: 13 * mib, quotaBytes: 15 * mib)
        await viewModel.load()
        #expect(viewModel.usageNotice != nil)
    }

    @Test("GST-006: sin sesión no hay aviso del 80 %, aunque quede una cuota guardada")
    func noUsageNoticeWithoutSession() async {
        let cache = PlansMemoryQuotaCache(StorageQuota(usedBytes: 12 * mib, quotaBytes: 15 * mib))
        let viewModel = LibraryViewModel(
            library: MutableLibrary([]), quotaCache: cache, usageNotice: MemoryUsageNotice(), isSignedIn: { false }
        )
        await viewModel.load()
        #expect(viewModel.usageNotice == nil)
        #expect(!viewModel.signedIn)
    }

    private let books = [book("nuevo", nil), book("leyendo", 42), book("terminado", 100)]

    @Test("LIB-010: al cargar muestra todos los libros y las cantidades de cada pestaña")
    func loadShowsAllBooks() async {
        let viewModel = LibraryViewModel(library: MutableLibrary(books))
        #expect(viewModel.loading)

        await viewModel.load()

        #expect(!viewModel.loading)
        #expect(viewModel.filter == .all)
        #expect(viewModel.books.map(\.id) == ["nuevo", "leyendo", "terminado"])
        #expect(viewModel.count(.all) == 3)
        #expect(viewModel.count(.reading) == 1)
        #expect(viewModel.count(.finished) == 1)
    }

    @Test("LIB-010: elegir Leyendo deja solo los libros en lectura, sin cambiar las cantidades")
    func selectingAFilterNarrowsTheGrid() async {
        let viewModel = LibraryViewModel(library: MutableLibrary(books))
        await viewModel.load()

        viewModel.select(.reading)

        #expect(viewModel.books.map(\.id) == ["leyendo"])
        #expect(viewModel.count(.all) == 3)
        viewModel.select(.finished)
        #expect(viewModel.books.map(\.id) == ["terminado"])
    }

    @Test("HOM-011: el filtro elegido desde Inicio se conserva al cargar")
    func filterChosenBeforeLoadingIsKept() async {
        let viewModel = LibraryViewModel(library: MutableLibrary(books))
        viewModel.select(.finished)

        await viewModel.load()

        #expect(viewModel.books.map(\.id) == ["terminado"])
    }

    @Test("LIB-001: biblioteca vacía solo después de cargar y sin ningún libro")
    func emptyLibrary() async {
        let empty = LibraryViewModel(library: MutableLibrary([]))
        #expect(!empty.isLibraryEmpty)
        await empty.load()
        #expect(empty.isLibraryEmpty)

        let onlyNew = LibraryViewModel(library: MutableLibrary([book("nuevo", nil)]))
        onlyNew.select(.finished)
        await onlyNew.load()
        #expect(!onlyNew.isLibraryEmpty)
        #expect(onlyNew.books.isEmpty)
    }

    @Test("SYN-001: volver a cargar trae lo que la sincronización agregó a la base")
    func reloadPicksUpNewBooks() async {
        let library = MutableLibrary([book("a", nil)])
        let viewModel = LibraryViewModel(library: library)
        await viewModel.load()

        await library.add(book("b", 50))
        await viewModel.load()

        #expect(viewModel.books.map(\.id) == ["a", "b"])
        #expect(viewModel.count(.reading) == 1)
    }
}
