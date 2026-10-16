import Foundation
import ReaderDomain
import Testing
@testable import Reader

// PRF-023: "Sincronizar solo con Wi-Fi", con un `UserDefaults` propio: nunca toca los datos de la app.

private actor CountingPass {
    private(set) var runs = 0
    func run() -> LibrarySyncOutcome {
        runs += 1
        return .done(SyncReport())
    }
}

@MainActor
private func settle(_ coordinator: SyncCoordinator) async {
    try? await Task.sleep(for: .milliseconds(50))
    await coordinator.idle()
}

private func freshDefaults() -> UserDefaults {
    UserDefaults(suiteName: "WifiOnlyTests-\(UUID().uuidString)")!
}

@MainActor
struct WifiOnlyTests {
    @Test("PRF-023: apagado por defecto; la elección se guarda en el dispositivo")
    func preference() {
        let defaults = freshDefaults()
        let gate = SyncNetworkGate(defaults: defaults, network: .metered)
        #expect(!gate.wifiOnly)
        #expect(gate.allowsSync)
        gate.setWifiOnly(true)
        #expect(defaults.bool(forKey: "sync.wifiOnly"))
        #expect(SyncNetworkGate(defaults: defaults).wifiOnly)
        #expect(!gate.allowsSync)
    }

    @Test("PRF-023: es del dispositivo: cerrar sesión no la borra")
    func survivesSignOut() {
        #expect(!LocalAccountWiper.accountKeys.contains(SyncNetworkGate.wifiOnlyKey))
    }

    @Test("PRF-023: con datos móviles no hay pasada y la biblioteca dice que espera Wi-Fi")
    func waitsForWifi() async {
        let defaults = freshDefaults()
        defaults.set(true, forKey: SyncNetworkGate.wifiOnlyKey)
        let gate = SyncNetworkGate(defaults: defaults, network: .metered)
        let pass = CountingPass()
        let coordinator = SyncCoordinator(pass: { _ in await pass.run() }, gate: gate) {}
        coordinator.request()
        await settle(coordinator)
        #expect(await pass.runs == 0)
        #expect(coordinator.state?.waitingForWifi == true)
        #expect(SyncStatusLine(state: coordinator.state!, pendingUploads: 2)?.text == "Esperando Wi-Fi para sincronizar.")
        #expect(SyncStatusLine(state: coordinator.state!, pendingUploads: 2)?.canRetry == false)
    }

    @Test("PRF-023: al llegar el Wi-Fi con la app a la vista, sincroniza solo")
    func syncsWhenWifiArrives() async {
        let defaults = freshDefaults()
        defaults.set(true, forKey: SyncNetworkGate.wifiOnlyKey)
        let gate = SyncNetworkGate(defaults: defaults, network: .metered)
        let pass = CountingPass()
        let coordinator = SyncCoordinator(pass: { _ in await pass.run() }, gate: gate) {}
        coordinator.request()
        await settle(coordinator)
        gate.networkChanged(.unmetered)
        await settle(coordinator)
        #expect(await pass.runs == 1)
        #expect(coordinator.state?.waitingForWifi == false)
    }

    @Test("PRF-023: con la app en segundo plano, el Wi-Fi no dispara una pasada; al volver, sí")
    func backgroundWaitsForReturn() async {
        let defaults = freshDefaults()
        defaults.set(true, forKey: SyncNetworkGate.wifiOnlyKey)
        let gate = SyncNetworkGate(defaults: defaults, network: .metered)
        let pass = CountingPass()
        let coordinator = SyncCoordinator(pass: { _ in await pass.run() }, gate: gate) {}
        coordinator.request()
        await settle(coordinator)
        coordinator.appWentToBackground()
        gate.networkChanged(.unmetered)
        await settle(coordinator)
        #expect(await pass.runs == 0)
        coordinator.appBecameActive()
        await settle(coordinator)
        #expect(await pass.runs == 1)
    }

    @Test("PRF-023: apagarlo sincroniza en el momento")
    func turningOffSyncs() async {
        let defaults = freshDefaults()
        defaults.set(true, forKey: SyncNetworkGate.wifiOnlyKey)
        let gate = SyncNetworkGate(defaults: defaults, network: .metered)
        let pass = CountingPass()
        let coordinator = SyncCoordinator(pass: { _ in await pass.run() }, gate: gate) {}
        coordinator.request()
        await settle(coordinator)
        gate.setWifiOnly(false)
        await settle(coordinator)
        #expect(await pass.runs == 1)
    }

    @Test("PRF-023: Perfil muestra el estado real del interruptor y lo cambia")
    func profileSwitch() async {
        let gate = SyncNetworkGate(defaults: freshDefaults(), network: .unmetered)
        let viewModel = ProfileViewModel(
            account: nil, quota: nil, cache: NoQuotaCache(), deviceName: "iPhone", settings: NoSettings(), wifiOnly: gate
        )
        #expect(viewModel.wifiOnly == false)
        viewModel.setWifiOnly(true)
        #expect(viewModel.wifiOnly == true)
        #expect(gate.wifiOnly)
    }
}

private struct NoQuotaCache: QuotaCache {
    func last() -> StorageQuota? { nil }
    func save(_ quota: StorageQuota) {}
}

private struct NoSettings: ReaderSettingsRepository {
    func load() -> ReaderSettings { ReaderSettings() }
    func save(_ settings: ReaderSettings) {}
}
