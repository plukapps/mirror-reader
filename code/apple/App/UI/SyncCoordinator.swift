import Foundation
import Observation
import ReaderDomain

/// Une la sincronización con las pantallas: la de la biblioteca (`SyncScheduler`, SYN-001) arranca con la app, se
/// pide al volver a ella y tras importar, y vuelve a leer la base cada vez que la cambia; la de la posición
/// (`PositionSync`, SYN-011, SYN-012) escucha mientras la app se ve y envía lo pendiente al pasar a segundo plano.
/// Sin Firebase no hace nada (ADR 0002).
@MainActor
@Observable
final class SyncCoordinator {
    /// Estado visible (SYN-008), o nil si la app funciona solo en local.
    private(set) var state: SyncState?

    /// Una pasada completa (`LibrarySync.run`), que llama al argumento cada vez que cambió la base local.
    typealias Pass = @Sendable (_ onChange: @escaping @Sendable () async -> Void) async -> LibrarySyncOutcome

    @ObservationIgnored private var scheduler: SyncScheduler?
    @ObservationIgnored private let positions: PositionSync?
    @ObservationIgnored private let reload: @MainActor () async -> Void
    @ObservationIgnored private var isActive = true
    @ObservationIgnored private var updates: Task<Void, Never>?

    /// - Parameters:
    ///   - pass: nil si la app funciona solo en local.
    ///   - positions: sincronización de la posición, o nil si la app funciona solo en local.
    ///   - reload: vuelve a leer la base en las pantallas.
    init(
        pass: Pass?,
        positions: PositionSync? = nil,
        ttl: Duration = SyncScheduler.defaultTTL,
        reload: @escaping @MainActor () async -> Void
    ) {
        self.reload = reload
        self.positions = positions
        guard let pass else { return }
        state = SyncState()
        let onChange: @Sendable () async -> Void = { [weak self] in await self?.changed() }
        scheduler = SyncScheduler(
            ttl: ttl,
            pass: { await pass(onChange) },
            onState: { [weak self] state in await self?.apply(state) }
        )
    }

    /// Pide una pasada que siempre corre: al arrancar, tras importar, al reintentar.
    func request() {
        guard let scheduler else { return }
        Task { await scheduler.request() }
    }

    /// La app volvió a primer plano: escucha las posiciones y sincroniza si la última pasada buena tiene más de
    /// 5 minutos.
    func appBecameActive() {
        isActive = true
        startListening()
        guard let scheduler else { return }
        Task { await scheduler.requestIfStale() }
    }

    /// La app dejó de verse: deja de escuchar y envía las posiciones pendientes (SYN-011). Sin trabajo en segundo plano.
    func appWentToBackground() {
        isActive = false
        guard let positions else { return }
        Task {
            await positions.stopListening()
            await positions.flush()
        }
    }

    /// Espera a que no quede ninguna pasada por correr. Para los tests.
    func idle() async {
        await scheduler?.idle()
    }

    private func startListening() {
        guard let positions, isActive else { return }
        Task { await positions.startListening() }
        // SYN-012: lo que llega de otros dispositivos cambia los porcentajes de Inicio y la biblioteca.
        guard updates == nil else { return }
        updates = Task { [weak self] in
            for await _ in await positions.remoteUpdates() {
                await self?.reload()
            }
        }
    }

    private func changed() async {
        await reload()
    }

    private func apply(_ newState: SyncState) async {
        state = newState
        guard !newState.running else { return }
        // Al arrancar la sesión llega con la primera pasada: recién entonces se puede escuchar.
        if newState.hasSession { startListening() }
        await reload()
    }
}
