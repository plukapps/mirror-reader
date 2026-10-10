import Foundation

/// Estado visible de la sincronización (SYN-008): si corre una pasada, el problema de la última y si hay sesión.
public struct SyncState: Equatable, Sendable {
    public var running: Bool
    public var issue: SyncIssue?
    /// Falso si la última pasada no encontró sesión: sin cuenta no hay nada que mostrar.
    public var hasSession: Bool

    public init(running: Bool = false, issue: SyncIssue? = nil, hasSession: Bool = true) {
        self.running = running
        self.issue = issue
        self.hasSession = hasSession
    }
}

/// Sincroniza la biblioteca sola (SYN-001), como `LibrarySync` de Android: al arrancar, cada vez que alguien lo pide
/// (importar, reintentar) y al volver a la app si pasó más de `ttl` desde la última pasada exitosa. Nunca corren dos
/// pasadas a la vez: un pedido durante una pasada hace que corra una más al terminar, así un libro importado a mitad
/// de camino no queda esperando.
///
/// Hay dos tipos de pedido. `request()` es explícito y siempre corre. `requestIfStale()` corre solo si la última
/// pasada exitosa es vieja: una pasada sin conexión, sin sesión o con error no cuenta, así que el próximo regreso a
/// la app reintenta. El momento de la última pasada exitosa vive solo en memoria: un proceso nuevo siempre
/// sincroniza. No hay sincronización con la app cerrada.
public actor SyncScheduler {
    /// Cada cuánto, como máximo, se repite la pasada al volver a la app.
    public static let defaultTTL: Duration = .seconds(5 * 60)

    private let ttl: Duration
    private let now: @Sendable () -> Duration
    private let pass: @Sendable () async -> LibrarySyncOutcome
    private let onState: @Sendable (SyncState) async -> Void

    public private(set) var state = SyncState()
    private var lastSuccessAt: Duration?
    private var explicitPending = false
    private var stalePending = false
    private var worker: Task<Void, Never>?

    /// - Parameters:
    ///   - now: reloj monótono (tiempo transcurrido desde un origen fijo).
    ///   - pass: una pasada completa (`LibrarySync.run`).
    ///   - onState: se llama con cada cambio de estado.
    public init(
        ttl: Duration = SyncScheduler.defaultTTL,
        now: @escaping @Sendable () -> Duration = SyncScheduler.monotonicNow,
        pass: @escaping @Sendable () async -> LibrarySyncOutcome,
        onState: @escaping @Sendable (SyncState) async -> Void = { _ in }
    ) {
        self.ttl = ttl
        self.now = now
        self.pass = pass
        self.onState = onState
    }

    /// Pide una pasada que siempre corre. Varios pedidos seguidos se juntan en uno.
    public func request() {
        explicitPending = true
        startWorkerIfNeeded()
    }

    /// Pide una pasada solo si la última exitosa es más vieja que el TTL. Para cuando la app vuelve a primer plano.
    public func requestIfStale() {
        stalePending = true
        startWorkerIfNeeded()
    }

    /// Espera a que no quede nada por correr.
    public func idle() async {
        await worker?.value
    }

    private func startWorkerIfNeeded() {
        guard worker == nil else { return }
        worker = Task { await drain() }
    }

    private func drain() async {
        while explicitPending || stalePending {
            let explicit = explicitPending
            explicitPending = false
            stalePending = false
            if explicit || isStale() { await runOnce() }
        }
        worker = nil
    }

    private func isStale() -> Bool {
        guard let lastSuccessAt else { return true }
        return now() - lastSuccessAt >= ttl
    }

    private func runOnce() async {
        await publish(SyncState(running: true, issue: state.issue, hasSession: state.hasSession))
        switch await pass() {
        case .noSession:
            lastSuccessAt = nil
            await publish(SyncState(hasSession: false))
        case let .done(report):
            lastSuccessAt = report.offline ? nil : now()
            await publish(SyncState(issue: report.issue))
        }
    }

    private func publish(_ newState: SyncState) async {
        state = newState
        await onState(newState)
    }

    private static let origin = ContinuousClock.now

    /// Tiempo transcurrido en un reloj monótono, que no salta si el usuario cambia la hora.
    public static let monotonicNow: @Sendable () -> Duration = { ContinuousClock.now - origin }
}
