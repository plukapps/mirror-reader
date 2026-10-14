import Foundation
import ReaderDomain

/// PLN-011: el aviso del 80 % ya se descartó en esta cuenta y este dispositivo. Es de la cuenta: se borra al cerrar
/// sesión (`LocalAccountWiper`). Misma clave que el DataStore de Android.
struct UserDefaultsUsageNotice: UsageNoticePreference, @unchecked Sendable {
    // `UserDefaults` es seguro entre hilos (documentación de Apple), aunque no esté marcado `Sendable`.
    private let defaults: UserDefaults

    init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
    }

    func isDismissed() -> Bool { defaults.bool(forKey: Self.key) }

    func dismiss() { defaults.set(true, forKey: Self.key) }

    static let key = "plans_usage_notice_dismissed"
}
