import Foundation

/// Lo que trajo una tanda de cambios de la nube. `newestUpdatedAt` es el mayor `updatedAt` del servidor entre ellos,
/// en milisegundos: desde ahí se pide la próxima vez.
public struct RemoteChanges: Equatable, Sendable {
    public let positions: [RemotePosition]
    public let newestUpdatedAt: Int64?

    public init(positions: [RemotePosition], newestUpdatedAt: Int64?) {
        self.positions = positions
        self.newestUpdatedAt = newestUpdatedAt
    }
}

/// Posición de lectura en la nube (SYN-011, SYN-012). Lanza `RemoteUnavailableError` sin conexión o sin sesión.
public protocol RemotePositions: Sendable {
    /// Envía la posición. Si la nube ya tiene una lectura más reciente no la pisa y termina bien: esa llegará por
    /// `observeChanges` o `fetch`.
    func push(_ position: ReadingPosition, deviceId: String, deviceName: String) async throws
    /// La posición del libro en la nube, directo del servidor, o nil si no hay (SYN-012).
    func fetch(bookId: String) async throws -> RemotePosition?
    /// Cambios con `updatedAt` posterior a `since` (todos si es nil), en vivo mientras se recorre. Un fallo termina la
    /// secuencia. No incluye escrituras pendientes de este mismo dispositivo.
    func observeChanges(since: Int64?) -> AsyncThrowingStream<RemoteChanges, any Error>
}

/// Lado local de la sincronización de la posición (SYN-011, SYN-012). La implementación vive en la capa de datos.
public protocol PositionSyncStore: Sendable {
    /// La posición guardada del libro y si la nube ya la tiene, o nil si nunca se leyó.
    func local(bookId: String) async -> LocalPosition?
    /// Posiciones que la nube aún no tiene y cuyo libro ya está en la nube (el servidor exige que el libro exista).
    func pendingPositions() async -> [ReadingPosition]
    /// Marca como enviada la lectura de `readAt`. Si mientras tanto se leyó más, sigue pendiente.
    func markSynced(bookId: String, readAt: Int64) async
    /// Vuelve a marcar la posición del libro como pendiente de enviar.
    func markPending(bookId: String) async
    /// Guarda la posición que vino de la nube como ya enviada, solo si es más reciente que la local.
    func applyRemote(_ position: ReadingPosition) async
}

/// Datos de este dispositivo para sincronizar la posición (SYN-011, SYN-012).
public protocol SyncPreferences: Sendable {
    /// Identificador de esta instalación. Se genera la primera vez y no cambia.
    func deviceId() async -> String
    /// Nombre legible del dispositivo, para avisar desde dónde se leyó.
    func deviceName() -> String
    /// Mayor `updatedAt` del servidor que ya se vio, en milisegundos, o nil si nunca se sincronizó.
    func lastPositionsSeenAt() async -> Int64?
    func setLastPositionsSeenAt(_ value: Int64) async
}

/// Sincroniza la posición de lectura entre dispositivos (SYN-011, SYN-012, ADR 0011), como `PositionSync` de Android.
///
/// - Enviar: solo al cerrar el libro o pasar a segundo plano, no en cada página. Lo que no se pudo enviar queda
///   pendiente en la base local y sale en el próximo `flush` (al terminar una pasada de la biblioteca o al volver a
///   cerrar un libro). Nunca corren dos envíos a la vez.
/// - Recibir: mientras la app está a la vista (`startListening` y `stopListening`), lo cambiado desde la última vez.
///   Gana la lectura más reciente (`mergePosition`).
///
/// Sin sesión no hace nada.
public actor PositionSync: PositionFlusher {
    private let remote: RemotePositions
    private let store: PositionSyncStore
    private let prefs: SyncPreferences
    private let account: AccountRepository

    private var flushing: Task<Void, Never>?
    private var listening: Task<Void, Never>?
    private var subscribers: [UUID: AsyncStream<RemotePosition>.Continuation] = [:]

    public init(remote: RemotePositions, store: PositionSyncStore, prefs: SyncPreferences, account: AccountRepository) {
        self.remote = remote
        self.store = store
        self.prefs = prefs
        self.account = account
    }

    // MARK: Enviar (SYN-011)

    public func flush() async {
        // Un envío a la vez: el que llega mientras otro corre espera a que termine y vuelve a mirar lo pendiente.
        while let running = flushing { await running.value }
        let task = Task {
            await sendPending()
            flushing = nil
        }
        flushing = task
        await task.value
    }

    /// Se cerró el libro: guarda la última posición (`save`) y envía. Corre aparte porque la pantalla ya se está yendo.
    public nonisolated func bookClosed(save: @escaping @Sendable () async -> Void) {
        Task {
            await save()
            await flush()
        }
    }

    private func sendPending() async {
        guard await account.currentUser() != nil else { return }
        let pending = await store.pendingPositions()
        guard !pending.isEmpty else { return }
        let deviceId = await prefs.deviceId()
        let deviceName = prefs.deviceName()
        for position in pending {
            do {
                try await remote.push(position, deviceId: deviceId, deviceName: deviceName)
                await store.markSynced(bookId: position.bookId, readAt: position.readAt)
            } catch is RemoteUnavailableError {
                return
            } catch {
                // Otro fallo (por ejemplo el libro aún no está en la nube): queda pendiente y se reintenta.
            }
        }
    }

    // MARK: Recibir (SYN-012, SYN-013)

    /// Posiciones más recientes que otro dispositivo escribió y que se guardaron aquí (SYN-013).
    public func remoteUpdates() -> AsyncStream<RemotePosition> {
        let id = UUID()
        let (stream, continuation) = AsyncStream<RemotePosition>.makeStream(bufferingPolicy: .bufferingNewest(16))
        subscribers[id] = continuation
        continuation.onTermination = { [weak self] _ in
            Task { await self?.unsubscribe(id) }
        }
        return stream
    }

    private func unsubscribe(_ id: UUID) {
        subscribers[id] = nil
    }

    /// Empieza a recibir cambios mientras la app está a la vista. Si ya escucha, o no hay sesión, no hace nada: se
    /// vuelve a llamar al volver a la app o cuando aparece la sesión.
    public func startListening() async {
        guard listening == nil, await account.currentUser() != nil else { return }
        // Mientras se consultaba la sesión pudo empezar otra escucha.
        guard listening == nil else { return }
        listening = Task {
            await listen()
            listening = nil
        }
    }

    /// Deja de recibir cambios: la app dejó de verse. No hay trabajo en segundo plano.
    public func stopListening() {
        listening?.cancel()
        listening = nil
    }

    private func listen() async {
        do {
            for try await changes in remote.observeChanges(since: await prefs.lastPositionsSeenAt()) {
                if Task.isCancelled { return }
                await apply(changes)
            }
        } catch {
            // La escucha se cortó (sin conexión, sin permiso): se retoma la próxima vez que la app vuelva a verse.
        }
    }

    private func apply(_ changes: RemoteChanges) async {
        let thisDevice = await prefs.deviceId()
        var needsFlush = false
        for incoming in changes.positions {
            let bookId = incoming.position.bookId
            let local = await store.local(bookId: bookId)
            switch mergePosition(local: local, remote: incoming) {
            case .useRemote:
                await store.applyRemote(incoming.position)
                if incoming.deviceId != thisDevice {
                    subscribers.values.forEach { $0.yield(incoming) }
                }
            case .pushLocal:
                // La local es más nueva. Si estaba marcada como enviada, la nube quedó atrás: se vuelve a enviar.
                if local?.isSynced == true { await store.markPending(bookId: bookId) }
                needsFlush = true
            case .markSynced:
                await store.markSynced(bookId: bookId, readAt: incoming.position.readAt)
            case .nothing:
                break
            }
        }
        if let newest = changes.newestUpdatedAt, newest > (await prefs.lastPositionsSeenAt() ?? 0) {
            await prefs.setLastPositionsSeenAt(newest)
        }
        if needsFlush { await flush() }
    }
}

/// Qué hacer con la posición al abrir un libro (SYN-003, SYN-012).
public enum OpeningPosition: Equatable, Sendable {
    /// Se abre donde está la local (o no hay otra).
    case local
    /// Había una lectura más reciente de otro dispositivo y la diferencia es chica: ya se guardó aquí.
    case updated
    /// Hay una lectura más reciente y la diferencia es grande: hay que preguntar.
    case confirm(remote: RemotePosition, local: ReadingPosition)
}

/// Al abrir un libro consulta su posición más reciente en la nube antes de mostrarlo (SYN-012), como
/// `ResolveOpeningPositionUseCase` de Android. Gana la lectura más reciente (SYN-003): hasta el 2 % de diferencia se usa
/// sin preguntar; más, se pregunta. Sin conexión, sin sesión o sin respuesta a tiempo se abre con la local: leer
/// nunca se bloquea.
public struct ResolveOpeningPosition: Sendable {
    /// Cuánto se espera la respuesta de la nube al abrir un libro.
    public static let defaultTimeout: Duration = .seconds(2)

    private let remote: RemotePositions
    private let store: PositionSyncStore
    private let positions: ReadingPositionRepository
    private let account: AccountRepository
    private let timeout: Duration

    public init(
        remote: RemotePositions,
        store: PositionSyncStore,
        positions: ReadingPositionRepository,
        account: AccountRepository,
        timeout: Duration = ResolveOpeningPosition.defaultTimeout
    ) {
        self.remote = remote
        self.store = store
        self.positions = positions
        self.account = account
        self.timeout = timeout
    }

    public func callAsFunction(bookId: String) async -> OpeningPosition {
        guard await account.currentUser() != nil, let incoming = await fetchWithTimeout(bookId: bookId) else { return .local }
        let local = await store.local(bookId: bookId)
        switch mergePosition(local: local, remote: incoming) {
        case .useRemote:
            if let local, isBigJump(local.position, incoming.position) {
                return .confirm(remote: incoming, local: local.position)
            }
            await store.applyRemote(incoming.position)
            return .updated
        case .markSynced:
            await store.markSynced(bookId: bookId, readAt: incoming.position.readAt)
            return .local
        case .pushLocal, .nothing:
            return .local
        }
    }

    /// El usuario eligió continuar desde la lectura del otro dispositivo.
    public func accept(_ remote: RemotePosition) async {
        await store.applyRemote(remote.position)
    }

    /// El usuario eligió quedarse. Se vuelve a guardar para que pase a ser la lectura más reciente y la nube no la pise.
    public func decline(_ local: ReadingPosition) async {
        await positions.save(bookId: local.bookId, position: SavedPosition(locatorJson: local.locatorJson, progress: local.progress))
    }

    /// Lo primero que llegue: la respuesta o el fin de la espera. No espera a que la consulta termine (el SDK no
    /// siempre atiende la cancelación), así una nube lenta no demora la apertura.
    private func fetchWithTimeout(bookId: String) async -> RemotePosition? {
        let remote = remote
        let timeout = timeout
        return await withCheckedContinuation { continuation in
            let once = ResumeOnce(continuation)
            Task { once.resume(try? await remote.fetch(bookId: bookId)) }
            Task {
                try? await Task.sleep(for: timeout)
                once.resume(nil)
            }
        }
    }

    // Sin progresión en alguna de las dos no se puede medir la diferencia: se pregunta.
    private func isBigJump(_ local: ReadingPosition, _ remote: ReadingPosition) -> Bool {
        guard let a = local.progress, let b = remote.progress else { return true }
        return exceedsJumpThreshold(abs(b - a))
    }
}

/// Reanuda una continuación una sola vez, con el primer valor que llegue.
private final class ResumeOnce<Value: Sendable>: @unchecked Sendable {
    private let lock = NSLock()
    private var continuation: CheckedContinuation<Value, Never>?

    init(_ continuation: CheckedContinuation<Value, Never>) { self.continuation = continuation }

    func resume(_ value: Value) {
        let pending = lock.withLock {
            defer { continuation = nil }
            return continuation
        }
        pending?.resume(returning: value)
    }
}
