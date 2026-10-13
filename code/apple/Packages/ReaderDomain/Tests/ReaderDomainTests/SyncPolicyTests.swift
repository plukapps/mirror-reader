import Testing
@testable import ReaderDomain

struct SyncPolicyTests {
    @Test("PRF-023: apagado, se sincroniza con cualquier red (sin red lo resuelve la pasada)")
    func offAllowsAnyNetwork() {
        #expect(canSync(wifiOnly: false, network: .unmetered))
        #expect(canSync(wifiOnly: false, network: .metered))
        #expect(canSync(wifiOnly: false, network: .offline))
    }

    @Test("PRF-023: encendido, solo con Wi-Fi o por cable")
    func onNeedsUnmetered() {
        #expect(canSync(wifiOnly: true, network: .unmetered))
        #expect(!canSync(wifiOnly: true, network: .metered))
        #expect(!canSync(wifiOnly: true, network: .offline))
    }
}
