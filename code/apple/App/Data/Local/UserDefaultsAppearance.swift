import Foundation
import ReaderDomain

/// Apariencia elegida en Perfil (PRF-021), en `UserDefaults` con la misma clave que el DataStore de Android. Es del
/// dispositivo: `LocalAccountWiper` no la borra al cerrar sesión.
struct UserDefaultsAppearance: AppearanceRepository, @unchecked Sendable {
    static let key = "appearance"
    // `UserDefaults` es seguro entre hilos (documentación de Apple), aunque no esté marcado `Sendable`.
    private let defaults: UserDefaults

    init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
    }

    func load() -> Appearance {
        Appearance(stored: defaults.string(forKey: Self.key))
    }

    func save(_ appearance: Appearance) {
        defaults.set(appearance.rawValue, forKey: Self.key)
    }
}
