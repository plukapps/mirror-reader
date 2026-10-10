import Foundation
import ReaderDomain
import Testing
@testable import Reader

private struct StubLibrary: LibraryRepository {
    let stored: [LibraryBook]
    func books() async -> [LibraryBook] { stored }
}

/// Biblioteca que cambia cuando corre la sincronización falsa.
private actor SyncingLibrary: LibraryRepository {
    private var stored: [LibraryBook] = []
    private(set) var syncs = 0

    func books() async -> [LibraryBook] { stored }

    func sync(_ outcome: LibrarySyncOutcome, adding books: [LibraryBook], onChange: @Sendable () async -> Void) async -> LibrarySyncOutcome {
        syncs += 1
        stored += books
        if !books.isEmpty { await onChange() }
        return outcome
    }
}

private let cloudBook = LibraryBook(id: "nube", title: "Walden", author: nil, coverPath: nil, progressPercent: 30, lastReadAt: Date(timeIntervalSince1970: 100))

@MainActor
struct HomeViewModelTests {
    private func date(hour: Int) -> Date {
        Calendar.current.date(from: DateComponents(year: 2026, month: 10, day: 8, hour: hour))!
    }

    @Test("HOM-002: al cargar, Inicio muestra el contenido de la biblioteca")
    func loadComputesHomeContent() async {
        let books = [
            LibraryBook(id: "ayer", title: "A", author: nil, coverPath: nil, progressPercent: 42, lastReadAt: Date(timeIntervalSince1970: 100)),
            LibraryBook(id: "hoy", title: "B", author: nil, coverPath: nil, progressPercent: 10, lastReadAt: Date(timeIntervalSince1970: 200)),
        ]
        let viewModel = HomeViewModel(library: StubLibrary(stored: books))
        #expect(viewModel.loading)

        await viewModel.load()

        #expect(!viewModel.loading)
        #expect(viewModel.content.continueReading?.id == "hoy")
        #expect(viewModel.content.reading.map(\.id) == ["ayer"])
    }

    @Test("HOM-003: biblioteca vacía")
    func emptyLibrary() async {
        let viewModel = HomeViewModel(library: StubLibrary(stored: []))
        await viewModel.load()
        #expect(viewModel.content.libraryEmpty)
        #expect(viewModel.content.continueReading == nil)
    }

    @Test("HOM-001: el saludo sale de la hora de apertura")
    func greetingUsesHourOfNow() {
        #expect(HomeViewModel(library: StubLibrary(stored: []), now: date(hour: 9)).greeting == .morning)
        #expect(HomeViewModel(library: StubLibrary(stored: []), now: date(hour: 22)).greeting == .night)
    }

    @Test("LIB-007: tras sincronizar, Inicio muestra los libros que llegaron de la nube")
    func reloadsAfterSync() async {
        let library = SyncingLibrary()
        let viewModel = HomeViewModel(library: library) { onChange in
            await library.sync(.synced(books: 1, positions: 1, covers: 0), adding: [cloudBook], onChange: onChange)
        }

        await viewModel.load()

        #expect(!viewModel.loading)
        #expect(viewModel.content.continueReading?.id == "nube")
    }

    @Test("SYN-001: la sincronización corre una vez aunque Inicio vuelva a aparecer")
    func syncsOncePerLaunch() async {
        let library = SyncingLibrary()
        let viewModel = HomeViewModel(library: library) { onChange in await library.sync(.offline, adding: [], onChange: onChange) }

        await viewModel.load()
        await viewModel.load()

        #expect(await library.syncs == 1)
    }

    @Test("SYN-001: volver a Inicio relee la base sin sincronizar (la sincronización la arranca la app)")
    func reloadDoesNotSync() async {
        let library = SyncingLibrary()
        let viewModel = HomeViewModel(library: library) { onChange in await library.sync(.offline, adding: [], onChange: onChange) }

        await viewModel.reload()

        #expect(await library.syncs == 0)
        #expect(!viewModel.loading)
    }

    @Test("ADR 0002: sin conexión, Inicio muestra lo local")
    func offlineKeepsLocal() async {
        let viewModel = HomeViewModel(library: StubLibrary(stored: [cloudBook])) { _ in .offline }
        await viewModel.load()
        #expect(viewModel.content.continueReading?.id == "nube")
    }
}
