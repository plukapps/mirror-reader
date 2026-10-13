import Foundation
import Observation
import ReaderDomain

/// PRF-023: "Sincronizar solo con Wi-Fi". Junta la elección (en `UserDefaults`, del dispositivo: cerrar sesión no la
/// borra) con la red actual y avisa cuando cambia si se puede sincronizar. La red la informa `PathNetworkMonitor`.
@MainActor
@Observable
final class SyncNetworkGate {
    static let wifiOnlyKey = "sync.wifiOnly"

    private(set) var wifiOnly: Bool
    private(set) var network: NetworkType
    /// Se llama cuando cambia `allowsSync`, con el valor nuevo. Lo usa `SyncCoordinator`.
    @ObservationIgnored var onChange: ((_ allowsSync: Bool) -> Void)?
    @ObservationIgnored private let defaults: UserDefaults

    /// - Parameter network: la red hasta el primer aviso del monitor. Sin Wi-Fi confirmado no se sincroniza si se eligió
    ///   solo Wi-Fi.
    init(defaults: UserDefaults = .standard, network: NetworkType = .offline) {
        self.defaults = defaults
        self.network = network
        wifiOnly = defaults.bool(forKey: Self.wifiOnlyKey)
    }

    var allowsSync: Bool { canSync(wifiOnly: wifiOnly, network: network) }

    func setWifiOnly(_ on: Bool) {
        update {
            wifiOnly = on
            defaults.set(on, forKey: Self.wifiOnlyKey)
        }
    }

    func networkChanged(_ network: NetworkType) {
        update { self.network = network }
    }

    private func update(_ change: () -> Void) {
        let before = allowsSync
        change()
        if allowsSync != before { onChange?(allowsSync) }
    }
}
