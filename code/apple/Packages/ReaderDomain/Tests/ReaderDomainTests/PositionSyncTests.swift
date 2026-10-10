import Foundation
import Testing
@testable import ReaderDomain

// Mismos casos que `PositionSyncTest` y `ResolveOpeningPositionUseCaseTest` de Android (SYN-003, SYN-010 a SYN-013).

/// Sesión falsa.
actor FakeSession: AccountRepository {
    var user: AccountUser?
    init(_ user: AccountUser?) { self.user = user }
    func set(_ user: AccountUser?) { self.user = user }
    func currentUser() async -> AccountUser? { user }
    func signIn(email: String, password: String) async throws -> AccountUser { throw RemoteUnavailableError("no") }
}

/// La nube, la base local y las preferencias de la sincronización de posiciones, en un solo falso, como
/// `FakePositionBackend` de Android. `calls` registra lo que pasa, en orden.
actor FakePositionBackend: RemotePositions, PositionSyncStore, SyncPreferences {
    var calls: [String] = []

    // La nube
    var offline = false
    var rejection: (any Error)?
    private(set) var pushes: [(position: ReadingPosition, deviceId: String, deviceName: String)] = []
    private(set) var pushAttempts = 0
    private var gates: [CheckedContinuation<Void, Never>] = []
    private var holdPushes = false
    private(set) var observedSince: [Int64?] = []
    private var listeners: [AsyncThrowingStream<RemoteChanges, any Error>.Continuation] = []
    var fetchResult: Result<RemotePosition?, any Error> = .success(nil)
    var fetchDelay: Duration = .zero

    // Local
    var local: [String: LocalPosition] = [:]

    // Preferencias
    var seenAt: Int64?

    func savedLocally(_ bookId: String, readAt: Int64, synced: Bool = false, locator: String = "{}") {
        local[bookId] = LocalPosition(position: Self.position(bookId, readAt, locator: locator), isSynced: synced)
    }

    func set(offline: Bool) { self.offline = offline }
    func set(rejection: (any Error)?) { self.rejection = rejection }
    func set(seenAt: Int64?) { self.seenAt = seenAt }
    func set(fetch: Result<RemotePosition?, any Error>, delay: Duration = .zero) {
        fetchResult = fetch
        fetchDelay = delay
    }
    func record(_ call: String) { calls.append(call) }

    /// Los envíos esperan a `releasePushes()`.
    func holdPushesUntilReleased() { holdPushes = true }
    func releasePushes() {
        holdPushes = false
        gates.forEach { $0.resume() }
        gates = []
    }
    var isHoldingAPush: Bool { !gates.isEmpty }

    func emit(_ positions: [RemotePosition], newest: Int64? = 1_000) {
        listeners.forEach { $0.yield(RemoteChanges(positions: positions, newestUpdatedAt: newest)) }
    }

    static func position(_ bookId: String, _ readAt: Int64, locator: String = "{}", progress: Double? = 0.5) -> ReadingPosition {
        ReadingPosition(bookId: bookId, locatorJson: locator, progress: progress, readAt: readAt)
    }

    static func remote(_ bookId: String, _ readAt: Int64, device: String = "otro", locator: String = "{}", progress: Double? = 0.5) -> RemotePosition {
        RemotePosition(position: position(bookId, readAt, locator: locator, progress: progress), deviceId: device, deviceName: "SM-X510")
    }

    // RemotePositions
    func push(_ position: ReadingPosition, deviceId: String, deviceName: String) async throws {
        pushAttempts += 1
        if holdPushes { await withCheckedContinuation { gates.append($0) } }
        if offline { throw RemoteUnavailableError("sin red") }
        if let rejection { throw rejection }
        calls.append("enviar:\(position.bookId)")
        pushes.append((position, deviceId, deviceName))
    }

    func fetch(bookId: String) async throws -> RemotePosition? {
        if fetchDelay > .zero { try? await Task.sleep(for: fetchDelay) }
        return try fetchResult.get()
    }

    nonisolated func observeChanges(since: Int64?) -> AsyncThrowingStream<RemoteChanges, any Error> {
        let (stream, continuation) = AsyncThrowingStream<RemoteChanges, any Error>.makeStream()
        Task { await self.register(continuation, since: since) }
        return stream
    }

    private func register(_ continuation: AsyncThrowingStream<RemoteChanges, any Error>.Continuation, since: Int64?) {
        observedSince.append(since)
        listeners.append(continuation)
    }

    // PositionSyncStore
    func local(bookId: String) async -> LocalPosition? { local[bookId] }

    func pendingPositions() async -> [ReadingPosition] {
        local.values.filter { !$0.isSynced }.map(\.position).sorted { $0.bookId < $1.bookId }
    }

    func markSynced(bookId: String, readAt: Int64) async {
        guard let current = local[bookId], current.position.readAt == readAt else { return }
        local[bookId] = LocalPosition(position: current.position, isSynced: true)
    }

    func markPending(bookId: String) async {
        guard let current = local[bookId] else { return }
        local[bookId] = LocalPosition(position: current.position, isSynced: false)
    }

    func applyRemote(_ position: ReadingPosition) async {
        let current = local[position.bookId]
        if current == nil || current!.position.readAt < position.readAt {
            local[position.bookId] = LocalPosition(position: position, isSynced: true)
        }
    }

    // SyncPreferences
    func deviceId() async -> String { "este-dispositivo" }
    nonisolated func deviceName() -> String { "iPhone" }
    func lastPositionsSeenAt() async -> Int64? { seenAt }
    func setLastPositionsSeenAt(_ value: Int64) async { seenAt = value }
}

/// Espera hasta que `condition` se cumpla, como mucho un segundo.
func eventually(_ condition: () async -> Bool) async -> Bool {
    for _ in 0..<200 {
        if await condition() { return true }
        try? await Task.sleep(for: .milliseconds(5))
    }
    return await condition()
}

private actor Announced {
    private(set) var books: [String] = []
    func add(_ bookId: String) { books.append(bookId) }
}

private let session = AccountUser(id: "u1", email: nil)

private func makeSync(_ backend: FakePositionBackend, account: FakeSession = FakeSession(session)) -> PositionSync {
    PositionSync(remote: backend, store: backend, prefs: backend, account: account)
}

struct PositionSyncSendTests {
    @Test("SYN-011: lo pendiente sale con el identificador y el nombre de este dispositivo y queda enviado")
    func flushSendsPending() async {
        let backend = FakePositionBackend()
        await backend.savedLocally("a", readAt: 10)
        await backend.savedLocally("b", readAt: 20, synced: true)

        await makeSync(backend).flush()

        #expect(await backend.calls == ["enviar:a"])
        let push = await backend.pushes.first
        #expect(push?.position == FakePositionBackend.position("a", 10))
        #expect(push?.deviceId == "este-dispositivo")
        #expect(push?.deviceName == "iPhone")
        #expect(await backend.local["a"]?.isSynced == true)
    }

    @Test("SYN-011: sin pendientes no se toca la nube")
    func nothingPending() async {
        let backend = FakePositionBackend()
        await backend.savedLocally("a", readAt: 10, synced: true)
        await makeSync(backend).flush()
        #expect(await backend.pushAttempts == 0)
    }

    @Test("SYN-001: sin conexión todo queda pendiente y no se intenta con el resto")
    func offlineStops() async {
        let backend = FakePositionBackend()
        await backend.set(offline: true)
        await backend.savedLocally("a", readAt: 10)
        await backend.savedLocally("b", readAt: 20)
        await makeSync(backend).flush()
        #expect(await backend.pendingPositions().map(\.bookId) == ["a", "b"])
        #expect(await backend.pushAttempts == 1)
    }

    @Test("SYN-011: un rechazo (el libro aún no está en la nube) queda pendiente y se reintenta")
    func rejectionStaysPending() async {
        let backend = FakePositionBackend()
        await backend.set(rejection: CocoaError(.fileNoSuchFile))
        await backend.savedLocally("a", readAt: 10)
        let sync = makeSync(backend)
        await sync.flush()
        #expect(await backend.pendingPositions().map(\.bookId) == ["a"])
        await backend.set(rejection: nil)
        await sync.flush()
        #expect(await backend.pendingPositions().isEmpty)
    }

    @Test("SYN-011: sin sesión no se envía nada")
    func noSession() async {
        let backend = FakePositionBackend()
        await backend.savedLocally("a", readAt: 10)
        await makeSync(backend, account: FakeSession(nil)).flush()
        #expect(await backend.pushAttempts == 0)
    }

    @Test("SYN-011: nunca corren dos envíos a la vez")
    func flushesDoNotOverlap() async {
        let backend = FakePositionBackend()
        await backend.holdPushesUntilReleased()
        await backend.savedLocally("a", readAt: 10)
        let sync = makeSync(backend)
        async let first: Void = sync.flush()
        #expect(await eventually { await backend.isHoldingAPush })
        async let second: Void = sync.flush()
        try? await Task.sleep(for: .milliseconds(20))
        await backend.releasePushes()
        _ = await (first, second)
        #expect(await backend.pushes.count == 1)
    }

    @Test("SYN-011: si se leyó más mientras se enviaba, la lectura nueva sigue pendiente")
    func newerReadingStaysPending() async {
        let backend = FakePositionBackend()
        await backend.holdPushesUntilReleased()
        await backend.savedLocally("a", readAt: 10)
        let sync = makeSync(backend)
        async let flushing: Void = sync.flush()
        #expect(await eventually { await backend.isHoldingAPush })
        await backend.savedLocally("a", readAt: 20)
        await backend.releasePushes()
        await flushing
        #expect(await backend.local["a"]?.isSynced == false)
        #expect(await backend.local["a"]?.position.readAt == 20)
    }

    @Test("SYN-011: al cerrar el libro se guarda la última posición y recién entonces se envía")
    func closingSavesThenSends() async {
        let backend = FakePositionBackend()
        let sync = makeSync(backend)
        await sync.bookClosed {
            await backend.record("guardar")
            await backend.savedLocally("a", readAt: 30)
        }
        #expect(await eventually { await backend.calls == ["guardar", "enviar:a"] })
    }
}

struct PositionSyncListenTests {
    @Test("SYN-012: una lectura más nueva de otro dispositivo se guarda aquí y se anuncia (SYN-013)")
    func newerRemoteAppliedAndAnnounced() async {
        let backend = FakePositionBackend()
        await backend.savedLocally("a", readAt: 10, synced: true)
        let sync = makeSync(backend)
        let announced = Announced()
        let updates = await sync.remoteUpdates()
        let listener = Task { for await update in updates { await announced.add(update.position.bookId) } }
        defer { listener.cancel() }
        await sync.startListening()
        #expect(await eventually { await backend.observedSince.count == 1 })

        await backend.emit([FakePositionBackend.remote("a", 50, locator: "nueva")])

        #expect(await eventually { await announced.books == ["a"] })
        #expect(await backend.local["a"]?.position.locatorJson == "nueva")
        #expect(await backend.local["a"]?.isSynced == true)
        #expect(await backend.seenAt == 1_000)
    }

    @Test("SYN-013: el eco de lo propio no se anuncia y marca la local como enviada")
    func echoNotAnnounced() async {
        let backend = FakePositionBackend()
        await backend.savedLocally("a", readAt: 10)
        let sync = makeSync(backend)
        let announced = Announced()
        let updates = await sync.remoteUpdates()
        let listener = Task { for await update in updates { await announced.add(update.position.bookId) } }
        defer { listener.cancel() }
        await sync.startListening()
        #expect(await eventually { await backend.observedSince.count == 1 })

        await backend.emit([FakePositionBackend.remote("a", 10, device: "este-dispositivo")])

        #expect(await eventually { await backend.local["a"]?.isSynced == true })
        #expect(await announced.books.isEmpty)
    }

    @Test("SYN-013: una lectura más nueva de este mismo dispositivo (reinstalación) se aplica sin anunciarse")
    func ownNewerReadingNotAnnounced() async {
        let backend = FakePositionBackend()
        await backend.savedLocally("a", readAt: 10, synced: true)
        let sync = makeSync(backend)
        let announced = Announced()
        let updates = await sync.remoteUpdates()
        let listener = Task { for await update in updates { await announced.add(update.position.bookId) } }
        defer { listener.cancel() }
        await sync.startListening()
        #expect(await eventually { await backend.observedSince.count == 1 })

        await backend.emit([FakePositionBackend.remote("a", 40, device: "este-dispositivo")])

        #expect(await eventually { await backend.local["a"]?.position.readAt == 40 })
        #expect(await announced.books.isEmpty)
    }

    @Test("SYN-010: una lectura local más nueva no se pisa y se envía")
    func newerLocalKeptAndSent() async {
        let backend = FakePositionBackend()
        await backend.savedLocally("a", readAt: 90, locator: "local")
        let sync = makeSync(backend)
        await sync.startListening()
        #expect(await eventually { await backend.observedSince.count == 1 })

        await backend.emit([FakePositionBackend.remote("a", 50, locator: "remota")])

        #expect(await eventually { await backend.calls == ["enviar:a"] })
        #expect(await backend.local["a"]?.position.locatorJson == "local")
    }

    @Test("SYN-010: una local marcada como enviada pero por delante de la nube se vuelve a enviar")
    func syncedButAheadIsResent() async {
        let backend = FakePositionBackend()
        await backend.savedLocally("a", readAt: 90, synced: true)
        let sync = makeSync(backend)
        await sync.startListening()
        #expect(await eventually { await backend.observedSince.count == 1 })

        await backend.emit([FakePositionBackend.remote("a", 50)])

        #expect(await eventually { await backend.calls == ["enviar:a"] })
        #expect(await backend.local["a"]?.isSynced == true)
    }

    @Test("SYN-012: se pide desde el último instante visto; la primera vez, todo")
    func listensFromLastSeen() async {
        let backend = FakePositionBackend()
        await makeSync(backend).startListening()
        #expect(await eventually { await backend.observedSince == [nil] })

        let other = FakePositionBackend()
        await other.set(seenAt: 777)
        await makeSync(other).startListening()
        #expect(await eventually { await other.observedSince == [777] })
    }

    @Test("SYN-012: empezar dos veces escucha una sola; después de parar se puede volver a empezar")
    func startStop() async {
        let backend = FakePositionBackend()
        let sync = makeSync(backend)
        await sync.startListening()
        await sync.startListening()
        #expect(await eventually { await backend.observedSince.count == 1 })
        try? await Task.sleep(for: .milliseconds(20))
        #expect(await backend.observedSince.count == 1)
        await sync.stopListening()
        await sync.startListening()
        #expect(await eventually { await backend.observedSince.count == 2 })
    }

    @Test("SYN-012: al dejar de verse la app se deja de aplicar cambios")
    func stoppingStopsApplying() async {
        let backend = FakePositionBackend()
        await backend.savedLocally("a", readAt: 10, synced: true)
        let sync = makeSync(backend)
        await sync.startListening()
        #expect(await eventually { await backend.observedSince.count == 1 })
        await sync.stopListening()

        await backend.emit([FakePositionBackend.remote("a", 50)])
        try? await Task.sleep(for: .milliseconds(30))

        #expect(await backend.local["a"]?.position.readAt == 10)
    }

    @Test("SYN-012: sin sesión no escucha; con sesión, al volver a pedirlo, sí")
    func waitsForSession() async {
        let backend = FakePositionBackend()
        let account = FakeSession(nil)
        let sync = makeSync(backend, account: account)
        await sync.startListening()
        try? await Task.sleep(for: .milliseconds(20))
        #expect(await backend.observedSince.isEmpty)

        await account.set(session)
        await sync.startListening()
        #expect(await eventually { await backend.observedSince.count == 1 })
    }

    @Test("SYN-012: el instante visto nunca retrocede y sin cambios no se mueve")
    func seenAtNeverGoesBack() async {
        let backend = FakePositionBackend()
        await backend.set(seenAt: 5_000)
        let sync = makeSync(backend)
        await sync.startListening()
        #expect(await eventually { await backend.observedSince.count == 1 })
        await backend.emit([FakePositionBackend.remote("a", 50)], newest: 2_000)
        #expect(await eventually { await backend.local["a"] != nil })
        #expect(await backend.seenAt == 5_000)

        let other = FakePositionBackend()
        let otherSync = makeSync(other)
        await otherSync.startListening()
        #expect(await eventually { await other.observedSince.count == 1 })
        await other.emit([], newest: nil)
        try? await Task.sleep(for: .milliseconds(20))
        #expect(await other.seenAt == nil)
    }
}

struct ResolveOpeningPositionTests {
    private actor SavedPositions: ReadingPositionRepository {
        private(set) var saved: [(String, SavedPosition)] = []
        func position(bookId: String) async -> SavedPosition? { nil }
        func save(bookId: String, position: SavedPosition) async { saved.append((bookId, position)) }
    }

    private func local(_ backend: FakePositionBackend, _ readAt: Int64, _ progress: Double?, synced: Bool = true) async {
        await backend.applyLocal(LocalPosition(
            position: ReadingPosition(bookId: "a", locatorJson: "local", progress: progress, readAt: readAt),
            isSynced: synced
        ))
    }

    private func useCase(
        _ backend: FakePositionBackend,
        session: AccountUser? = AccountUser(id: "u1", email: nil),
        positions: SavedPositions = SavedPositions(),
        timeout: Duration = .seconds(2)
    ) -> ResolveOpeningPosition {
        ResolveOpeningPosition(remote: backend, store: backend, positions: positions, account: FakeSession(session), timeout: timeout)
    }

    private func remote(_ readAt: Int64, _ progress: Double?) -> RemotePosition {
        FakePositionBackend.remote("a", readAt, locator: "remota", progress: progress)
    }

    @Test("SYN-012: sin sesión se usa la posición local")
    func noSession() async {
        let backend = FakePositionBackend()
        await backend.set(fetch: .success(remote(50, 0.9)))
        #expect(await useCase(backend, session: nil)(bookId: "a") == .local)
    }

    @Test("SYN-012: sin conexión leer no se bloquea")
    func offline() async {
        let backend = FakePositionBackend()
        await local(backend, 10, 0.1)
        await backend.set(fetch: .failure(RemoteUnavailableError("sin red")))
        #expect(await useCase(backend)(bookId: "a") == .local)
        #expect(await backend.local["a"]?.position.locatorJson == "local")
    }

    @Test("SYN-012: sin nada en la nube se queda la local")
    func nothingInCloud() async {
        let backend = FakePositionBackend()
        await local(backend, 10, 0.1)
        #expect(await useCase(backend)(bookId: "a") == .local)
    }

    @Test("SYN-012: una nube lenta no demora la apertura más que el tiempo máximo")
    func slowCloud() async {
        let backend = FakePositionBackend()
        await local(backend, 10, 0.1)
        await backend.set(fetch: .success(remote(50, 0.9)), delay: .seconds(5))
        let clock = ContinuousClock()
        let start = clock.now
        let result = await useCase(backend, timeout: .milliseconds(100))(bookId: "a")
        #expect(result == .local)
        #expect(clock.now - start < .seconds(2))
    }

    @Test("SYN-003: sin posición local se usa la de la nube sin preguntar")
    func noLocalTakesCloud() async {
        let backend = FakePositionBackend()
        await backend.set(fetch: .success(remote(50, 0.6)))
        #expect(await useCase(backend)(bookId: "a") == .updated)
        #expect(await backend.local["a"]?.position.locatorJson == "remota")
    }

    @Test("SYN-003: hasta el 2 % de diferencia salta sola")
    func smallJump() async {
        let backend = FakePositionBackend()
        await local(backend, 10, 0.50)
        await backend.set(fetch: .success(remote(50, 0.52)))
        #expect(await useCase(backend)(bookId: "a") == .updated)
        #expect(await backend.local["a"]?.position.locatorJson == "remota")
    }

    @Test("SYN-003: más del 2 % pregunta y no cambia nada todavía")
    func bigJumpAsks() async {
        let backend = FakePositionBackend()
        await local(backend, 10, 0.50)
        await backend.set(fetch: .success(remote(50, 0.53)))
        let result = await useCase(backend)(bookId: "a")
        guard case let .confirm(remote, local) = result else {
            Issue.record("esperaba una pregunta, llegó \(result)")
            return
        }
        #expect(remote.position.locatorJson == "remota")
        #expect(local.locatorJson == "local")
        #expect(await backend.local["a"]?.position.locatorJson == "local")
    }

    @Test("SYN-003: sin progresión no se puede medir y se pregunta")
    func unknownProgressAsks() async {
        let backend = FakePositionBackend()
        await local(backend, 10, nil)
        await backend.set(fetch: .success(remote(50, 0.5)))
        guard case .confirm = await useCase(backend)(bookId: "a") else {
            Issue.record("esperaba una pregunta")
            return
        }
    }

    @Test("SYN-010: una lectura local más nueva no se pisa")
    func newerLocalKept() async {
        let backend = FakePositionBackend()
        await local(backend, 90, 0.1, synced: false)
        await backend.set(fetch: .success(remote(50, 0.9)))
        #expect(await useCase(backend)(bookId: "a") == .local)
        #expect(await backend.local["a"]?.position.locatorJson == "local")
    }

    @Test("SYN-011: la misma lectura marca la local como enviada")
    func echoMarksSynced() async {
        let backend = FakePositionBackend()
        await local(backend, 50, 0.5, synced: false)
        await backend.set(fetch: .success(remote(50, 0.5)))
        #expect(await useCase(backend)(bookId: "a") == .local)
        #expect(await backend.local["a"]?.isSynced == true)
    }

    @Test("SYN-003: aceptar aplica la posición de la nube")
    func accept() async {
        let backend = FakePositionBackend()
        await local(backend, 10, 0.1)
        await useCase(backend).accept(remote(50, 0.9))
        #expect(await backend.local["a"]?.position.locatorJson == "remota")
    }

    @Test("SYN-003: quedarse vuelve a guardar la local para que sea la más reciente")
    func decline() async {
        let backend = FakePositionBackend()
        let positions = SavedPositions()
        await useCase(backend, positions: positions).decline(ReadingPosition(bookId: "a", locatorJson: "local", progress: 0.1, readAt: 10))
        let saved = await positions.saved
        #expect(saved.count == 1)
        #expect(saved.first?.0 == "a")
        #expect(saved.first?.1 == SavedPosition(locatorJson: "local", progress: 0.1))
    }
}

extension FakePositionBackend {
    func applyLocal(_ position: LocalPosition) { local[position.position.bookId] = position }
}
