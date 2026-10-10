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
    private let sync: Sync?
    private var synced = false

    /// Trae la nube a la base local (`LibrarySync`) y llama al argumento cada vez que la base cambió.
    typealias Sync = @Sendable (_ onChange: @escaping @Sendable () async -> Void) async -> LibrarySyncOutcome

    /// - Parameter sync: nil si la app funciona solo en local.
    init(
        library: LibraryRepository,
        now: Date = .now,
        calendar: Calendar = .current,
        sync: Sync? = nil
    ) {
        self.library = library
        self.sync = sync
        greeting = ReaderDomain.greeting(forHour: calendar.component(.hour, from: now))
    }

    /// Muestra primero lo local (ADR 0002) y, la primera vez, sincroniza y vuelve a leer la base cada vez que
    /// cambia (SYN-001).
    func load() async {
        await reload()
        guard let sync, !synced else { return }
        synced = true
        _ = await sync { [weak self] in await self?.reload() }
    }

    /// Vuelve a leer la base, sin sincronizar. Lo usa la pantalla al aparecer.
    func reload() async {
        content = homeContent(await library.books())
        loading = false
    }
}
