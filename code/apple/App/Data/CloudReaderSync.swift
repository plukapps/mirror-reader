import Foundation
import ReaderDomain

/// La posición de la nube para el lector: consulta al abrir (`ResolveOpeningPosition`) y avisos y envíos
/// (`PositionSync`), ADR 0011.
struct CloudReaderSync: ReaderPositionSync {
    let resolver: ResolveOpeningPosition
    let positions: PositionSync
    /// PRF-023: si la red permite sincronizar. Sin ella, se abre con la posición local y no se envía nada.
    var allowed: @Sendable () async -> Bool = { true }

    func resolve(bookId: String) async -> OpeningPosition {
        guard await allowed() else { return .local }
        return await resolver(bookId: bookId)
    }
    func accept(_ remote: RemotePosition) async { await resolver.accept(remote) }
    func decline(_ local: ReadingPosition) async { await resolver.decline(local) }
    func remoteUpdates() async -> AsyncStream<RemotePosition> { await positions.remoteUpdates() }

    func sendPending() {
        let positions = positions
        let allowed = allowed
        Task { if await allowed() { await positions.flush() } }
    }
}
