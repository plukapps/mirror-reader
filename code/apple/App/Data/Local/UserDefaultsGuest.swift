import Foundation
import ReaderDomain

/// GST-002: bienvenida ya vista en este dispositivo (`welcome_seen`, la clave de Android). Se borra al cerrar sesión
/// (`LocalAccountWiper`, GST-016).
struct UserDefaultsWelcome: WelcomePreference, @unchecked Sendable {
    // `UserDefaults` es seguro entre hilos (documentación de Apple), aunque no esté marcado `Sendable`.
    private let defaults: UserDefaults

    init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
    }

    func seen() -> Bool { defaults.bool(forKey: Self.key) }

    func markSeen() { defaults.set(true, forKey: Self.key) }

    static let key = "welcome_seen"
}

/// GST-010: cuándo se cerró el aviso sin cuenta (`guest_notice_dismissed_at`, en milisegundos como en Android). Se
/// borra al cerrar sesión (`LocalAccountWiper`).
struct UserDefaultsGuestNotice: GuestNoticePreference, @unchecked Sendable {
    private let defaults: UserDefaults

    init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
    }

    func dismissedAt() -> Date? {
        guard defaults.object(forKey: Self.key) != nil else { return nil }
        return Date(timeIntervalSince1970: defaults.double(forKey: Self.key) / 1000)
    }

    func dismiss(at date: Date) { defaults.set(date.timeIntervalSince1970 * 1000, forKey: Self.key) }

    static let key = "guest_notice_dismissed_at"
}
