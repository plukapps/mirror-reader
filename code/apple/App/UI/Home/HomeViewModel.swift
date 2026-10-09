import Foundation
import Observation
import ReaderDomain

/// Estado de Inicio (HOM-001 a HOM-003, HOM-008 a HOM-010).
@MainActor
@Observable
final class HomeViewModel {
    private(set) var loading = true
    private(set) var content = HomeContent.empty
    /// Se calcula al abrir la pantalla y no cambia mientras está abierta.
    let greeting: Greeting

    private let library: LibraryRepository

    init(library: LibraryRepository, now: Date = .now, calendar: Calendar = .current) {
        self.library = library
        greeting = ReaderDomain.greeting(forHour: calendar.component(.hour, from: now))
    }

    func load() async {
        content = homeContent(await library.books())
        loading = false
    }
}
