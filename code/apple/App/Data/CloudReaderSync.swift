import Foundation
import ReaderDomain

/// La posición de la nube para el lector: consulta al abrir (`ResolveOpeningPosition`) y avisos y envíos
/// (`PositionSync`), ADR 0011.
struct CloudReaderSync: ReaderPositionSync {
    let resolver: ResolveOpeningPosition
    let positions: PositionSync

    func resolve(bookId: String) async -> OpeningPosition { await resolver(bookId: bookId) }
    func accept(_ remote: RemotePosition) async { await resolver.accept(remote) }
    func decline(_ local: ReadingPosition) async { await resolver.decline(local) }
    func remoteUpdates() async -> AsyncStream<RemotePosition> { await positions.remoteUpdates() }

    func sendPending() {
        let positions = positions
        Task { await positions.flush() }
    }
}
