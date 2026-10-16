import Foundation

/// Apariencia de la app (PRF-021): clara, oscura o la del dispositivo. Es de este dispositivo: no se sincroniza. Los
/// valores guardados son los mismos que Android.
public enum Appearance: String, CaseIterable, Sendable {
    case light = "LIGHT"
    case dark = "DARK"
    case system = "SYSTEM"

    /// PRF-021: por defecto, la del dispositivo.
    public static let `default` = Appearance.system

    /// La guardada, o la de por defecto si no hay o no se reconoce.
    public init(stored: String?) {
        self = stored.flatMap(Appearance.init(rawValue:)) ?? .default
    }
}

/// Dónde se guarda la apariencia elegida.
public protocol AppearanceRepository: Sendable {
    func load() -> Appearance
    func save(_ appearance: Appearance)
}
