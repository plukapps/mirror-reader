import Foundation
import ReaderDomain
import Testing
@testable import Reader

private struct StubLibrary: LibraryRepository {
    let stored: [LibraryBook]
    func books() async -> [LibraryBook] { stored }
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

    @Test("ADR 0002: sin conexión, Inicio muestra lo local")
    func offlineKeepsLocal() async {
        let viewModel = HomeViewModel(library: StubLibrary(stored: [cloudBook]))
        await viewModel.load()
        #expect(viewModel.content.continueReading?.id == "nube")
    }
}
