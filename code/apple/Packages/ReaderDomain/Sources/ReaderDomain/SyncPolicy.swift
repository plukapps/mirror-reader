import Foundation

/// La red del dispositivo, vista desde la sincronización (PRF-023).
public enum NetworkType: Equatable, Sendable {
    case offline
    /// Wi-Fi o por cable.
    case unmetered
    /// Datos móviles u otra red.
    case metered
}

/// PRF-023: con "Sincronizar solo con Wi-Fi" encendido, solo se sincroniza con Wi-Fi o por cable. Apagado, siempre se
/// intenta (sin red, la pasada lo informa como siempre). Como `canSync` de Android.
public func canSync(wifiOnly: Bool, network: NetworkType) -> Bool {
    !wifiOnly || network == .unmetered
}
