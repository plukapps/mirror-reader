import ReaderDomain
import SwiftUI

/// La apariencia de la app a la vista (PRF-021): la raíz la aplica y Perfil la cambia, en el momento.
@MainActor
@Observable
final class AppearanceSettings {
    private(set) var appearance: Appearance
    @ObservationIgnored private let repository: AppearanceRepository

    init(repository: AppearanceRepository) {
        self.repository = repository
        appearance = repository.load()
    }

    /// Para `.preferredColorScheme`: nil sigue al dispositivo (y cambia con él).
    var colorScheme: ColorScheme? {
        switch appearance {
        case .light: .light
        case .dark: .dark
        case .system: nil
        }
    }

    func select(_ appearance: Appearance) {
        guard appearance != self.appearance else { return }
        self.appearance = appearance
        repository.save(appearance)
    }
}
