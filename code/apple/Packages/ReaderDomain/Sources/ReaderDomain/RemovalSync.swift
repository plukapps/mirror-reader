import Foundation

/// Libros borrados de la cuenta en la nube (LIB-020, LIB-008, SYN-007). Lanza `RemoteUnavailableError` si no hay
/// conexión o sesión.
public protocol RemoteRemovals: Sendable {
    /// Marca el libro como borrado (`deletedAt`). Desde ahí no existe para la cuenta. Repetirlo no hace daño.
    func markDeleted(bookId: String) async throws
    /// Borra el EPUB y la portada de la nube. Lo que ya no está cuenta como borrado.
    func deleteFiles(bookId: String) async throws
    /// Ids de los libros marcados como borrados.
    func deletedBookIds() async throws -> [String]
    /// Los mismos ids cada vez que cambian, mientras se escucha.
    func observeDeleted() -> AsyncThrowingStream<[String], any Error>
}

/// Libros quitados de este dispositivo y su paso por la nube (LIB-020). Como `BookRemovals` de Android.
public protocol BookRemovals: Sendable {
    /// Libros quitados aquí que la nube todavía no sabe que se borraron.
    func pendingCloudRemovals() async -> [String]
    /// La nube ya los tiene borrados (marca y archivos).
    func markCloudRemoved(bookId: String) async
    /// Quita de este dispositivo los libros borrados desde otro (LIB-008). Solo los que ya estaban en la nube: uno
    /// importado aquí y aún no subido es otra copia y no se toca. Los que no están aquí se ignoran.
    func removeDeletedInCloud(bookIds: [String]) async
}

/// El paso de borrados de la pasada de sincronización.
public protocol CloudRemovals: Sendable {
    /// Envía los borrados de aquí y aplica los de la nube. Lanza `RemoteUnavailableError` sin conexión.
    func sync() async throws
}

/// Borrar un libro de la cuenta (LIB-020, LIB-008), como `RemovalSync` de Android. El dispositivo que lo quita ya lo
/// borró de su base; aquí:
/// - se envía a la nube: primero la marca, después los archivos; recién entonces queda hecho. Sin conexión queda
///   pendiente y lo reintenta la próxima pasada.
/// - se aplican los borrados de otros dispositivos: con la app a la vista, al llegar; si no, al sincronizar. Un libro
///   abierto en el lector se quita al cerrarlo.
///
/// Sin sesión no hace nada.
public actor RemovalSync: CloudRemovals {
    private let remote: RemoteRemovals
    private let local: BookRemovals
    private let account: AccountRepository
    private var open: Set<String> = []
    private var waitingForClose: Set<String> = []
    private var listening: Task<Void, Never>?
    private var pushing: Task<Void, any Error>?
    private var subscribers: [UUID: AsyncStream<Void>.Continuation] = [:]

    public init(remote: RemoteRemovals, local: BookRemovals, account: AccountRepository) {
        self.remote = remote
        self.local = local
        self.account = account
    }

    public func sync() async throws {
        guard await account.currentUser() != nil else { return }
        try await pushPending()
        await apply(try await remote.deletedBookIds())
    }

    /// Envía lo pendiente ahora (al quitar un libro). Sin conexión queda para la próxima pasada.
    public func push() async {
        guard await account.currentUser() != nil else { return }
        try? await pushPending()
    }

    /// Espera al envío en curso antes de empezar otro. Si igual coinciden dos, repetir un borrado no hace daño.
    private func pushPending() async throws {
        if let running = pushing { _ = try? await running.value }
        let task = Task {
            for bookId in await local.pendingCloudRemovals() {
                try await remote.markDeleted(bookId: bookId)
                try await remote.deleteFiles(bookId: bookId)
                await local.markCloudRemoved(bookId: bookId)
            }
        }
        pushing = task
        defer { pushing = nil }
        try await task.value
    }

    private func apply(_ bookIds: [String]) async {
        let later = bookIds.filter { open.contains($0) }
        waitingForClose.formUnion(later)
        let now = bookIds.filter { !open.contains($0) }
        guard !now.isEmpty else { return }
        await local.removeDeletedInCloud(bookIds: now)
        notify()
    }

    // MARK: Lector abierto

    public func bookOpened(_ bookId: String) {
        open.insert(bookId)
    }

    public func bookClosed(_ bookId: String) async {
        open.remove(bookId)
        guard waitingForClose.remove(bookId) != nil else { return }
        await local.removeDeletedInCloud(bookIds: [bookId])
        notify()
    }

    // MARK: Escuchar (con la app a la vista)

    public func startListening() async {
        guard listening == nil, await account.currentUser() != nil else { return }
        guard listening == nil else { return }
        listening = Task {
            do {
                for try await ids in remote.observeDeleted() { await apply(ids) }
            } catch {
                // Sin conexión: la próxima pasada aplica lo que falte.
            }
            listening = nil
        }
    }

    public func stopListening() {
        listening?.cancel()
        listening = nil
    }

    /// Avisa cada vez que se quitaron libros por un borrado de otro dispositivo, para recargar las pantallas.
    public func removals() -> AsyncStream<Void> {
        let id = UUID()
        let (stream, continuation) = AsyncStream<Void>.makeStream(bufferingPolicy: .bufferingNewest(1))
        subscribers[id] = continuation
        continuation.onTermination = { _ in Task { await self.unsubscribe(id) } }
        return stream
    }

    private func unsubscribe(_ id: UUID) {
        subscribers[id] = nil
    }

    private func notify() {
        subscribers.values.forEach { $0.yield() }
    }
}
