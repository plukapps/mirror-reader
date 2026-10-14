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
    @ObservationIgnored private let removals: RemovalSync?
    @ObservationIgnored private let watcher: LibraryWatcher?
    /// ACC-011: interruptores remotos; escuchan mientras la app se ve.
    @ObservationIgnored let flags: FeatureFlags
    /// SYN-016: notas entre dispositivos, o nil si la app funciona solo en local.
    @ObservationIgnored private let notes: NoteSync?
    @ObservationIgnored private var noteUpdates: Task<Void, Never>?
    /// PRF-023: si la red permite sincronizar ("Sincronizar solo con Wi-Fi"). nil: siempre.
    @ObservationIgnored private let gate: SyncNetworkGate?
    @ObservationIgnored private var removalUpdates: Task<Void, Never>?
    @ObservationIgnored private let reload: @MainActor () async -> Void
    @ObservationIgnored private var isActive = true
    @ObservationIgnored private var updates: Task<Void, Never>?

    /// - Parameters:
    ///   - pass: nil si la app funciona solo en local.
    ///   - positions: sincronización de la posición, o nil si la app funciona solo en local.
    ///   - gate: "Sincronizar solo con Wi-Fi" (PRF-023): sin red apta no hay pasadas, envíos ni escuchas.
    ///   - reload: vuelve a leer la base en las pantallas.
    init(
        pass: Pass?,
        positions: PositionSync? = nil,
        removals: RemovalSync? = nil,
        watcher: LibraryWatcher? = nil,
        gate: SyncNetworkGate? = nil,
        flags: FeatureFlags = FeatureFlags(source: nil),
        notes: NoteSync? = nil,
        ttl: Duration = SyncScheduler.defaultTTL,
        reload: @escaping @MainActor () async -> Void
    ) {
        self.reload = reload
        self.positions = positions
        self.removals = removals
        self.watcher = watcher
        self.gate = gate
        self.flags = flags
        self.notes = notes
        guard let pass else { return }
        state = SyncState()
        let onChange: @Sendable () async -> Void = { [weak self] in await self?.changed() }
        scheduler = SyncScheduler(
            ttl: ttl,
            pass: { await pass(onChange) },
            allowed: { [weak gate] in await gate?.allowsSync ?? true },
            onState: { [weak self] state in await self?.apply(state) }
        )
        gate?.onChange = { [weak self] allowed in self?.networkGateChanged(allowed) }
    }

    /// PRF-023: la red permite sincronizar (o no hay "solo Wi-Fi").
    private var cloudAllowed: Bool { gate?.allowsSync ?? true }

    /// Pide una pasada que siempre corre: al arrancar, tras importar, al reintentar.
    func request() {
        guard let scheduler else { return }
        Task { await scheduler.request() }
    }

    /// La app volvió a primer plano: escucha las posiciones y sincroniza si la última pasada buena tiene más de
    /// 5 minutos.
    func appBecameActive() {
        isActive = true
        // ACC-011: primero los interruptores, que deciden qué más se sincroniza.
        Task { await flags.startListening() }
        startListening()
        guard let scheduler else { return }
        Task { await scheduler.requestIfStale() }
    }

    /// La app dejó de verse: deja de escuchar y envía las posiciones pendientes (SYN-011). Sin trabajo en segundo plano.
    func appWentToBackground() {
        isActive = false
        Task { await flags.stopListening() }
        if let notes { Task { await notes.stopListening() } }
        if let removals { Task { await removals.stopListening() } }
        if let watcher { Task { await watcher.stopListening() } }
        guard let positions, cloudAllowed else {
            if let positions { Task { await positions.stopListening() } }
            return
        }
        Task {
            await positions.stopListening()
            await positions.flush()
        }
    }

    /// LIB-020: un libro abierto en el lector no se quita hasta cerrarlo, aunque se haya borrado en otro dispositivo.
    func readerOpened(_ bookId: String) {
        guard let removals else { return }
        Task { await removals.bookOpened(bookId) }
    }

    func readerClosed(_ bookId: String) {
        guard let removals else { return }
        Task { await removals.bookClosed(bookId) }
    }

    /// SYN-011: envía las posiciones pendientes, si la red lo permite (PRF-023).
    func flushPositions() async {
        guard let positions, cloudAllowed else { return }
        await positions.flush()
    }

    /// LIB-020: borra de la cuenta los libros quitados, si la red lo permite (PRF-023).
    func pushRemovals() async {
        guard let removals, cloudAllowed else { return }
        await removals.push()
    }

    // MARK: Solo Wi-Fi (PRF-023)

    /// Con red apta y la app a la vista, vuelve a escuchar y sincroniza; sin ella, deja de escuchar y el estado pasa a
    /// "Esperando Wi-Fi".
    private func networkGateChanged(_ allowed: Bool) {
        if allowed {
            guard isActive else { return }
            startListening()
            request()
        } else {
            Task { await stopListeningForSignOut() }
            request()
        }
    }

    // MARK: Cerrar sesión (PRF-009)

    /// Deja de escuchar la nube y espera a que cada escucha se haya detenido.
    func stopListeningForSignOut() async {
        await notes?.stopListening()
        await positions?.stopListening()
        await removals?.stopListening()
        await watcher?.stopListening()
    }

    /// Corre `body` sin pasadas de biblioteca en curso ni nuevas (ver `SyncScheduler.whileIdle`).
    func whileIdle<T: Sendable>(_ body: @Sendable () async -> T) async -> T {
        guard let scheduler else { return await body() }
        return await scheduler.whileIdle(body)
    }

    /// Vuelve a escuchar si la app está a la vista; sin sesión, las escuchas no hacen nada.
    func resumeListening() {
        startListening()
    }

    /// Espera a que no quede ninguna pasada por correr. Para los tests.
    func idle() async {
        await scheduler?.idle()
    }

    private func startListening() {
        // PRF-023: sin red apta no se escucha la nube.
        guard cloudAllowed else { return }
        startListeningForRemovals()
        startListeningForNotes()
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

    /// LIB-020, SYN-015: con la app a la vista, los libros borrados en otro dispositivo se quitan al llegar y los
    /// agregados piden una pasada.
    private func startListeningForRemovals() {
        if let watcher, isActive { Task { await watcher.startListening() } }
        guard let removals, isActive else { return }
        Task { await removals.startListening() }
        guard removalUpdates == nil else { return }
        removalUpdates = Task { [weak self] in
            for await _ in await removals.removals() {
                await self?.reload()
            }
        }
    }

    /// SYN-016: con la app a la vista, las notas de otros dispositivos llegan solas.
    private func startListeningForNotes() {
        guard let notes, isActive else { return }
        Task { await notes.startListening() }
        guard noteUpdates == nil else { return }
        noteUpdates = Task { [weak self] in
            for await _ in await notes.changes() {
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

/// `SyncCoordinator` como lo ve el cierre de sesión (PRF-009).
struct CoordinatorSyncControl: SyncControl {
    let coordinator: SyncCoordinator

    func stopListening() async {
        await coordinator.stopListeningForSignOut()
    }

    func whileIdle<T: Sendable>(_ body: @Sendable () async -> T) async -> T {
        await coordinator.whileIdle(body)
    }

    func startListening() async {
        await coordinator.resumeListening()
    }
}
