import Foundation
import Testing
@testable import ReaderDomain

// Mismos casos que `LibrarySyncTest` de Android (orquestador): una pasada a la vez, pedidos que se juntan y TTL.

/// Reloj manual para el TTL.
private final class ManualClock: @unchecked Sendable {
    private let lock = NSLock()
    private var elapsed: Duration = .zero

    func now() -> Duration { lock.withLock { elapsed } }
    func advance(_ by: Duration) { lock.withLock { elapsed += by } }
}

/// Pasada falsa: cuenta las corridas, devuelve el resultado configurado y puede quedarse esperando hasta que se la suelte.
private actor FakePass {
    private(set) var runs = 0
    var outcome: LibrarySyncOutcome = .done(SyncReport())
    private var holding = false
    private var waiting: [CheckedContinuation<Void, Never>] = []
    private var started: [CheckedContinuation<Void, Never>] = []

    func set(_ outcome: LibrarySyncOutcome) { self.outcome = outcome }

    /// Las próximas pasadas esperan a `release()`.
    func hold() { holding = true }

    func release() {
        holding = false
        waiting.forEach { $0.resume() }
        waiting = []
    }

    /// Espera a que empiece una pasada retenida.
    func waitUntilStarted() async {
        if !waiting.isEmpty { return }
        await withCheckedContinuation { started.append($0) }
    }

    func run() async -> LibrarySyncOutcome {
        runs += 1
        if holding {
            await withCheckedContinuation { continuation in
                waiting.append(continuation)
                started.forEach { $0.resume() }
                started = []
            }
        }
        return outcome
    }
}

private actor StateLog {
    private(set) var states: [SyncState] = []
    func add(_ state: SyncState) { states.append(state) }
}

private func makeScheduler(_ pass: FakePass, clock: ManualClock = ManualClock(), log: StateLog? = nil) -> SyncScheduler {
    SyncScheduler(ttl: .seconds(300), now: { clock.now() }, pass: { await pass.run() }, onState: { await log?.add($0) })
}

struct SyncSchedulerTests {
    @Test("SYN-001: un pedido corre una pasada")
    func requestRuns() async {
        let pass = FakePass()
        let scheduler = makeScheduler(pass)
        await scheduler.request()
        await scheduler.idle()
        #expect(await pass.runs == 1)
    }

    @Test("SYN-001: un pedido durante una pasada agenda exactamente una más")
    func requestsDuringPassCollapse() async {
        let pass = FakePass()
        await pass.hold()
        let scheduler = makeScheduler(pass)
        await scheduler.request()
        await pass.waitUntilStarted()
        await scheduler.request()
        await scheduler.request()
        await scheduler.requestIfStale()
        await pass.release()
        await scheduler.idle()
        #expect(await pass.runs == 2)
    }

    @Test("SYN-001: con la app en marcha, volver a ella no sincroniza antes de 5 minutos")
    func ttlBlocksResync() async {
        let pass = FakePass()
        let clock = ManualClock()
        let scheduler = makeScheduler(pass, clock: clock)
        await scheduler.request()
        await scheduler.idle()
        clock.advance(.seconds(299))
        await scheduler.requestIfStale()
        await scheduler.idle()
        #expect(await pass.runs == 1)
    }

    @Test("SYN-001: pasados 5 minutos, volver a la app sincroniza")
    func ttlExpires() async {
        let pass = FakePass()
        let clock = ManualClock()
        let scheduler = makeScheduler(pass, clock: clock)
        await scheduler.request()
        await scheduler.idle()
        clock.advance(.seconds(300))
        await scheduler.requestIfStale()
        await scheduler.idle()
        #expect(await pass.runs == 2)
    }

    @Test("SYN-001: con el proceso nuevo, volver a la app siempre sincroniza")
    func newProcessAlwaysSyncs() async {
        let pass = FakePass()
        let scheduler = makeScheduler(pass)
        await scheduler.requestIfStale()
        await scheduler.idle()
        #expect(await pass.runs == 1)
    }

    @Test("SYN-001: al arrancar, el pedido de inicio y el de volver a la app hacen una sola pasada")
    func coldStartSyncsOnce() async {
        let pass = FakePass()
        let scheduler = makeScheduler(pass)
        await scheduler.request()
        await scheduler.idle()
        await scheduler.requestIfStale()
        await scheduler.idle()
        #expect(await pass.runs == 1)
    }

    @Test("SYN-008: una pasada sin conexión no consume el TTL")
    func offlineDoesNotConsumeTtl() async {
        let pass = FakePass()
        await pass.set(.done(SyncReport(unreachable: true)))
        let scheduler = makeScheduler(pass)
        await scheduler.request()
        await scheduler.idle()
        await scheduler.requestIfStale()
        await scheduler.idle()
        #expect(await pass.runs == 2)
    }

    @Test("SYN-001: sin sesión la pasada no consume el TTL")
    func noSessionDoesNotConsumeTtl() async {
        let pass = FakePass()
        await pass.set(.noSession)
        let scheduler = makeScheduler(pass)
        await scheduler.request()
        await scheduler.idle()
        await scheduler.requestIfStale()
        await scheduler.idle()
        #expect(await pass.runs == 2)
        #expect(await scheduler.state == SyncState(hasSession: false))
    }

    @Test("SYN-001: un pedido explícito (importar, reintentar) ignora el TTL")
    func explicitIgnoresTtl() async {
        let pass = FakePass()
        let scheduler = makeScheduler(pass)
        await scheduler.request()
        await scheduler.idle()
        await scheduler.request()
        await scheduler.idle()
        #expect(await pass.runs == 2)
    }

    @Test("SYN-001: un pedido por antigüedad no se traga uno explícito durante una pasada")
    func staleDoesNotSwallowExplicit() async {
        let pass = FakePass()
        await pass.hold()
        let scheduler = makeScheduler(pass)
        await scheduler.request()
        await pass.waitUntilStarted()
        await scheduler.requestIfStale()
        await scheduler.request()
        await pass.release()
        await scheduler.idle()
        #expect(await pass.runs == 2)
    }

    @Test("SYN-008: el estado muestra la pasada en curso y el último problema, y se limpia al salir bien")
    func stateReportsIssue() async {
        let pass = FakePass()
        let log = StateLog()
        let scheduler = makeScheduler(pass, log: log)
        await pass.set(.done(SyncReport(upload: UploadReport(notEnoughSpace: 1))))
        await scheduler.request()
        await scheduler.idle()
        #expect(await scheduler.state == SyncState(issue: .notEnoughSpace(1)))
        await pass.set(.done(SyncReport()))
        await scheduler.request()
        await scheduler.idle()
        #expect(await scheduler.state == SyncState())
        let states = await log.states
        #expect(states.first == SyncState(running: true))
        #expect(states.contains(SyncState(issue: .notEnoughSpace(1))))
    }
}
