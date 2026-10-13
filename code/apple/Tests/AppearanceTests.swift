import Foundation
import ReaderDomain
import SwiftUI
import Testing
@testable import Reader

private final class MemoryAppearance: AppearanceRepository, @unchecked Sendable {
    var stored: Appearance
    init(_ stored: Appearance = .system) { self.stored = stored }
    func load() -> Appearance { stored }
    func save(_ appearance: Appearance) { stored = appearance }
}

@MainActor
struct AppearanceTests {
    private let suite = "AppearanceTests-\(UUID().uuidString)"

    @Test("PRF-021: la preferencia se guarda en este dispositivo; por defecto, Sistema")
    func userDefaults() throws {
        let defaults = try #require(UserDefaults(suiteName: suite))
        defer { defaults.removePersistentDomain(forName: suite) }
        let repository = UserDefaultsAppearance(defaults: defaults)
        #expect(repository.load() == .system)
        repository.save(.dark)
        #expect(repository.load() == .dark)
        #expect(defaults.string(forKey: "appearance") == "DARK")
    }

    @Test("PRF-021: Claro y Oscuro fuerzan el modo; Sistema sigue al dispositivo")
    func colorScheme() {
        #expect(AppearanceSettings(repository: MemoryAppearance(.light)).colorScheme == .light)
        #expect(AppearanceSettings(repository: MemoryAppearance(.dark)).colorScheme == .dark)
        #expect(AppearanceSettings(repository: MemoryAppearance(.system)).colorScheme == nil)
    }

    @Test("PRF-021: elegir guarda y cambia en el momento")
    func select() {
        let repository = MemoryAppearance()
        let settings = AppearanceSettings(repository: repository)
        settings.select(.dark)
        #expect(settings.appearance == .dark)
        #expect(settings.colorScheme == .dark)
        #expect(repository.stored == .dark)
    }

    @Test("PRF-021: es del dispositivo: cerrar sesión no la borra")
    func survivesSignOut() {
        #expect(!LocalAccountWiper.accountKeys.contains(UserDefaultsAppearance.key))
    }
}
