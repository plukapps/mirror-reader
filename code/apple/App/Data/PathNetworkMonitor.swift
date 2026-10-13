import Foundation
import Network
import ReaderDomain

/// La red del dispositivo con `NWPathMonitor` (PRF-023): Wi-Fi o cable es `.unmetered`; cualquier otra, `.metered`.
/// Avisa la red actual al empezar y cada vez que cambia, en una cola propia.
final class PathNetworkMonitor: @unchecked Sendable {
    // `NWPathMonitor` es seguro entre hilos; se usa solo desde su cola después de `start`.
    private let monitor = NWPathMonitor()
    private let queue = DispatchQueue(label: "com.pluk.reader.network")

    func start(onChange: @escaping @Sendable (NetworkType) -> Void) {
        monitor.pathUpdateHandler = { path in onChange(Self.type(of: path)) }
        monitor.start(queue: queue)
    }

    static func type(of path: NWPath) -> NetworkType {
        guard path.status == .satisfied else { return .offline }
        return path.usesInterfaceType(.wifi) || path.usesInterfaceType(.wiredEthernet) ? .unmetered : .metered
    }
}
