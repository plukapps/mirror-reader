import Foundation

/// Libros de la cuenta que cambian en la nube, para enterarse sin esperar a la próxima pasada (SYN-014).
public protocol RemoteBookChanges: Sendable {
    /// Ids de los libros vivos (sin `deletedAt`) cambiados después de `since` (ms desde 1970), cada vez que llega algo
    /// nuevo, mientras se escucha.
    func observeAlive(since: Int64) -> AsyncThrowingStream<[String], any Error>
}

/// Ids de los libros que ya están en la biblioteca de este dispositivo (descargados o solo en la nube).
public protocol KnownBooks: Sendable {
    func knownBookIds() async -> Set<String>
}

/// Con la app a la vista, un libro agregado (o vuelto a importar) en otro dispositivo aparece solo (SYN-014), como
/// `LibraryWatcher` de Android: escucha los libros cambiados hace poco y, si alguno no está aquí, llama a
/// `onNewBooks` (que pide una pasada). Solo mira los últimos 15 minutos: cubre la app en segundo plano dentro del tope
/// de la pasada (5 minutos); pasado el tope, la pasada al volver ya lo trae.
public actor LibraryWatcher {
    public static let lookbackMillis: Int64 = 15 * 60_000

    private let remote: RemoteBookChanges
    private let known: KnownBooks
    private let account: AccountRepository
    private let now: @Sendable () -> Date
    private let onNewBooks: @Sendable () async -> Void
    private var listening: Task<Void, Never>?

    public init(
        remote: RemoteBookChanges,
        known: KnownBooks,
        account: AccountRepository,
        now: @escaping @Sendable () -> Date = { .now },
        onNewBooks: @escaping @Sendable () async -> Void
    ) {
        self.remote = remote
        self.known = known
        self.account = account
        self.now = now
        self.onNewBooks = onNewBooks
    }

    public func startListening() async {
        guard listening == nil, await account.currentUser() != nil else { return }
        guard listening == nil else { return }
        let since = Int64((now().timeIntervalSince1970 * 1000).rounded()) - Self.lookbackMillis
        listening = Task {
            do {
                for try await ids in remote.observeAlive(since: since) {
                    let local = await known.knownBookIds()
                    if ids.contains(where: { !local.contains($0) }) { await onNewBooks() }
                }
            } catch {
                // Sin conexión: la próxima pasada trae lo que falte.
            }
            listening = nil
        }
    }

    public func stopListening() {
        listening?.cancel()
        listening = nil
    }
}
