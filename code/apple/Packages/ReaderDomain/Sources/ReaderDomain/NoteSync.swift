import Foundation

/// Una nota tal como viaja a la nube o viene de ella (SYN-016). `deletedAt` no es nil si está borrada (SYN-007).
public struct SyncNote: Equatable, Sendable {
    public let note: Note
    public let deletedAt: Int64?

    public init(note: Note, deletedAt: Int64? = nil) {
        self.note = note
        self.deletedAt = deletedAt
    }

    /// Última edición o borrado, reloj del dispositivo (`editedAt` en la nube): decide quién gana (SYN-005).
    public var editedAt: Int64 { note.updatedAt }
}

/// Lado local de la sincronización de notas (SYN-016), como `NoteSyncStore` de Android.
public protocol NoteSyncStore: Sendable {
    /// Cambios que la nube no tiene y se pueden subir (el libro de una nota viva ya está en la nube).
    func pendingNotes() async -> [SyncNote]
    /// PRF-009: hay notas que se perderían al cerrar sesión. `syncOn`: con la sincronización apagada, cuentan todas.
    func hasUnsyncedNotes(syncOn: Bool) async -> Bool
    /// La nube ya tiene la versión de `editedAt`. Si mientras tanto se editó, sigue pendiente.
    func markNoteSynced(id: String, editedAt: Int64) async
    /// Aplica una nota de la nube solo si es más reciente que la local (SYN-005); un borrado la elimina (SYN-007).
    func applyRemote(_ remote: SyncNote) async
    /// Elimina las borradas que la nube ya tiene borradas.
    func purgeSyncedNoteDeletions() async
}

/// Una tanda de notas que cambiaron en la nube. `newestUpdatedAt`: mayor `updatedAt` del servidor, en milisegundos.
public struct RemoteNoteChanges: Equatable, Sendable {
    public let notes: [SyncNote]
    public let newestUpdatedAt: Int64?

    public init(notes: [SyncNote], newestUpdatedAt: Int64?) {
        self.notes = notes
        self.newestUpdatedAt = newestUpdatedAt
    }
}

/// Notas en la nube (SYN-016). Lanza `RemoteUnavailableError` sin conexión o sin sesión; otro error si el servidor la
/// rechaza.
public protocol RemoteNotes: Sendable {
    /// Sube la nota. Si la nube ya tiene una edición más reciente no la pisa y termina bien (SYN-005).
    func push(_ note: SyncNote) async throws
    /// Notas con `updatedAt` posterior a `since` (todas si es nil), en vivo mientras se recorre. Un fallo termina la
    /// secuencia. No incluye escrituras pendientes de este mismo dispositivo.
    func observeChanges(since: Int64?) -> AsyncThrowingStream<RemoteNoteChanges, any Error>
}

/// Desde dónde pedir los cambios de notas la próxima vez. Se borra al cerrar sesión, con el resto de los datos.
public protocol NoteSyncCursor: Sendable {
    func lastNotesSeenAt() async -> Int64?
    func setLastNotesSeenAt(_ value: Int64) async
}

/// Sube las notas pendientes (SYN-016). Lo usa `LibrarySync` al final de la pasada.
public protocol NoteFlusher: Sendable {
    func flush() async
}

/// Sincroniza las notas entre dispositivos (SYN-016, SYN-005, SYN-007), solo con la función prendida (SYN-017), como
/// `NoteSync` de Android.
///
/// - Subir: las pendientes, al guardar una nota, al terminar una pasada de la biblioteca y al prenderse la función.
/// - Bajar: mientras la app está a la vista, desde la última vez. Gana la edición más reciente; lo decide
///   `NoteSyncStore.applyRemote`.
/// - Apagada: no se sube, no se baja y no se borra nada de la nube. Las notas siguen en el dispositivo.
///
/// Sin sesión no hace nada.
public actor NoteSync: NoteFlusher {
    private let remote: RemoteNotes
    private let store: NoteSyncStore
    private let cursor: NoteSyncCursor
    private let flags: FeatureFlags
    private let account: AccountRepository
    private let allowed: @Sendable () async -> Bool

    private var flushing: Task<Void, Never>?
    private var watching: Task<Void, Never>?
    private var listening: Task<Void, Never>?
    private var subscribers: [UUID: AsyncStream<Void>.Continuation] = [:]

    /// - Parameter allowed: PRF-023, la red permite sincronizar ("solo Wi-Fi").
    public init(
        remote: RemoteNotes, store: NoteSyncStore, cursor: NoteSyncCursor, flags: FeatureFlags, account: AccountRepository,
        allowed: @escaping @Sendable () async -> Bool = { true }
    ) {
        self.remote = remote
        self.store = store
        self.cursor = cursor
        self.flags = flags
        self.account = account
        self.allowed = allowed
    }

    // MARK: Subir

    public func flush() async {
        // Una subida a la vez: la que llega mientras otra corre espera y vuelve a mirar lo pendiente.
        while let running = flushing { await running.value }
        let task = Task {
            await sendPending()
            flushing = nil
        }
        flushing = task
        await task.value
    }

    /// Sube sin esperar: el lector guardó o borró una nota.
    public nonisolated func requestFlush() {
        Task { await flush() }
    }

    private func sendPending() async {
        guard await flags.isEnabled(.notesSync), await allowed(), await account.currentUser() != nil else { return }
        for note in await store.pendingNotes() {
            do {
                try await remote.push(note)
                await store.markNoteSynced(id: note.note.id, editedAt: note.editedAt)
            } catch is RemoteUnavailableError {
                return
            } catch {
                // Otro rechazo (por ejemplo el libro todavía no llegó a la nube): queda pendiente y se reintenta.
            }
        }
        await store.purgeSyncedNoteDeletions()
    }

    // MARK: Bajar

    /// Cada vez que llegaron notas de la nube, para volver a leer la base en las pantallas.
    public func changes() -> AsyncStream<Void> {
        let id = UUID()
        let (stream, continuation) = AsyncStream<Void>.makeStream(bufferingPolicy: .bufferingNewest(1))
        subscribers[id] = continuation
        continuation.onTermination = { [weak self] _ in Task { await self?.unsubscribe(id) } }
        return stream
    }

    private func unsubscribe(_ id: UUID) {
        subscribers[id] = nil
    }

    /// Con la app a la vista. Sigue el interruptor: al prenderse sube lo pendiente y escucha; al apagarse deja de
    /// escuchar. Llamarlo otra vez no hace nada.
    public func startListening() async {
        guard watching == nil, await account.currentUser() != nil else { return }
        guard watching == nil else { return }
        let switches = await flags.changes(.notesSync)
        watching = Task { [weak self] in
            for await on in switches {
                guard let self else { return }
                if on {
                    await self.flush()
                    await self.listen()
                } else {
                    await self.stopRemote()
                }
            }
        }
    }

    public func stopListening() {
        watching?.cancel()
        watching = nil
        stopRemote()
    }

    private func listen() async {
        guard listening == nil, await allowed() else { return }
        let since = await cursor.lastNotesSeenAt()
        listening = Task { [weak self] in
            guard let self else { return }
            do {
                for try await changes in self.remote.observeChanges(since: since) {
                    if Task.isCancelled { return }
                    await self.apply(changes)
                }
            } catch {
                // La escucha se cortó (sin conexión, sin permiso): se retoma la próxima vez que la app vuelva a verse.
            }
            await self.listeningEnded()
        }
    }

    private func listeningEnded() {
        listening = nil
    }

    private func stopRemote() {
        listening?.cancel()
        listening = nil
    }

    private func apply(_ changes: RemoteNoteChanges) async {
        // SYN-017: apagada, lo que llega no se aplica.
        guard await flags.isEnabled(.notesSync) else { return }
        for note in changes.notes { await store.applyRemote(note) }
        if let newest = changes.newestUpdatedAt, newest > (await cursor.lastNotesSeenAt() ?? 0) {
            await cursor.setLastNotesSeenAt(newest)
        }
        subscribers.values.forEach { $0.yield() }
    }
}
