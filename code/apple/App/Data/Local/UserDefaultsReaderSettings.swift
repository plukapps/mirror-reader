import Foundation
import ReaderDomain

/// Ajustes de lectura en `UserDefaults` (ADR 0012), con las mismas claves y valores que el DataStore de Android.
/// Un valor desconocido vuelve al de fábrica.
struct UserDefaultsReaderSettings: ReaderSettingsRepository, @unchecked Sendable {
    // `UserDefaults` es seguro entre hilos (documentación de Apple), aunque no esté marcado `Sendable`.
    private let defaults: UserDefaults

    init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
    }

    func load() -> ReaderSettings {
        let fallback = ReaderSettings()
        return ReaderSettings(
            theme: defaults.string(forKey: Key.theme).flatMap(ReadingTheme.init(rawValue:)) ?? fallback.theme,
            fontTenths: defaults.object(forKey: Key.fontTenths) as? Int ?? fallback.fontTenths,
            font: defaults.string(forKey: Key.font).flatMap(ReaderFont.init(rawValue:)) ?? fallback.font,
            lineSpacing: defaults.string(forKey: Key.lineSpacing).flatMap(LineSpacing.init(rawValue:)) ?? fallback.lineSpacing
        )
    }

    func save(_ settings: ReaderSettings) {
        defaults.set(settings.theme.rawValue, forKey: Key.theme)
        defaults.set(settings.fontTenths, forKey: Key.fontTenths)
        defaults.set(settings.font.rawValue, forKey: Key.font)
        defaults.set(settings.lineSpacing.rawValue, forKey: Key.lineSpacing)
    }

    private enum Key {
        static let theme = "reader.theme"
        static let fontTenths = "reader.fontTenths"
        static let font = "reader.font"
        static let lineSpacing = "reader.lineSpacing"
    }
}
