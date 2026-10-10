import Testing
@testable import ReaderDomain

// Mismos casos que `PositionMergeTest` de Android. SYN-003, SYN-010: gana la lectura más reciente, no la que llegó
// último, y no se pierde nada.

private func position(_ readAt: Int64, progress: Double? = 0.5) -> ReadingPosition {
    ReadingPosition(bookId: "libro", locatorJson: "{}", progress: progress, readAt: readAt)
}

private func local(_ readAt: Int64, synced: Bool) -> LocalPosition { LocalPosition(position: position(readAt), isSynced: synced) }
private func remote(_ readAt: Int64) -> RemotePosition { RemotePosition(position: position(readAt), deviceId: "otro", deviceName: "SM-X510") }
private func remoteAt(_ progress: Double?) -> RemotePosition {
    RemotePosition(position: position(10, progress: progress), deviceId: "otro", deviceName: "SM-X510")
}

struct PositionMergeTests {
    @Test("SYN-003: sin nada en ningún lado no hay nada que hacer")
    func nothing() { #expect(mergePosition(local: nil, remote: nil) == .nothing) }

    @Test("SYN-001: lo leído sin conexión y no enviado sale cuando se puede")
    func unsyncedLocalIsPushed() { #expect(mergePosition(local: local(10, synced: false), remote: nil) == .pushLocal) }

    @Test("SYN-003: una local ya enviada sin nada en la nube no necesita nada")
    func syncedLocalNeedsNothing() { #expect(mergePosition(local: local(10, synced: true), remote: nil) == .nothing) }

    @Test("SYN-003: dispositivo nuevo, se toma la de la nube")
    func noLocalTakesRemote() { #expect(mergePosition(local: nil, remote: remote(10)) == .useRemote) }

    @Test("SYN-003: una lectura remota más nueva reemplaza a la local, aunque esté pendiente")
    func newerRemoteWins() {
        #expect(mergePosition(local: local(10, synced: true), remote: remote(11)) == .useRemote)
        #expect(mergePosition(local: local(10, synced: false), remote: remote(11)) == .useRemote)
    }

    @Test("SYN-010: una lectura local más nueva se envía y no se pisa, aunque figure como enviada")
    func newerLocalIsPushed() {
        #expect(mergePosition(local: local(12, synced: false), remote: remote(11)) == .pushLocal)
        #expect(mergePosition(local: local(12, synced: true), remote: remote(11)) == .pushLocal)
    }

    @Test("SYN-011: el eco de lo que este dispositivo envió solo marca la local como enviada")
    func echoMarksSynced() {
        #expect(mergePosition(local: local(10, synced: false), remote: remote(10)) == .markSynced)
        #expect(mergePosition(local: local(10, synced: true), remote: remote(10)) == .nothing)
    }

    @Test("SYN-003: el umbral del 2 % incluye exactamente el 2 %")
    func jumpThreshold() {
        #expect(!exceedsJumpThreshold(0.52 - 0.50))
        #expect(!exceedsJumpThreshold(0))
        #expect(exceedsJumpThreshold(0.03))
    }

    @Test("SYN-013: solo se ofrece seguir si el otro dispositivo leyó más adelante, por más del 0,5 %")
    func offerJump() {
        #expect(shouldOfferJump(currentProgress: 0.30, remote: remoteAt(0.60)))
        #expect(!shouldOfferJump(currentProgress: 0.60, remote: remoteAt(0.30)))
        #expect(shouldOfferJump(currentProgress: 0.50, remote: remoteAt(0.51)))
        #expect(!shouldOfferJump(currentProgress: 0.50, remote: remoteAt(0.505)))
        #expect(!shouldOfferJump(currentProgress: 0.50, remote: remoteAt(0.50)))
    }

    @Test("SYN-013: sin el avance del otro no se ofrece; sin el propio, sí")
    func offerJumpUnknown() {
        #expect(!shouldOfferJump(currentProgress: 0.30, remote: remoteAt(nil)))
        #expect(shouldOfferJump(currentProgress: nil, remote: remoteAt(0.60)))
    }
}
