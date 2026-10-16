import Foundation
import Observation
import ReaderDomain

/// Pantalla con la que abre la app (ONB-019), como `MainViewModel.start` de Android.
@MainActor
@Observable
final class AppStartViewModel {
    enum Start: Equatable { case loading, welcome, verifyEmail, home }

    private(set) var start: Start = .loading
    /// ONB-013: "Importar mis libros" abre el selector apenas aparece Inicio.
    private(set) var pendingImport = false

    /// nil si la app funciona solo en local (sin Firebase): no hay cuenta que crear y abre Inicio (ADR 0015).
    @ObservationIgnored private let account: AccountRepository?
    @ObservationIgnored private var decided = false

    init(account: AccountRepository?) {
        self.account = account
    }

    /// Se decide una vez por arranque, con la sesión que el dispositivo ya conoce (sin red). Si después la sesión
    /// cambia, no se reubica al usuario: el onboarding navega por su cuenta.
    func load() async {
        guard !decided else { return }
        decided = true
        guard let account else {
            start = .home
            return
        }
        start = switch startDestination(await account.currentUser(), welcomeSeen: false) {
        case .welcome: .welcome
        case .verifyEmail: .verifyEmail
        case .home: .home
        }
    }

    /// Fin del onboarding: a Inicio, y atrás ya no vuelve (ONB-013, ONB-016).
    func enterHome(importBooks: Bool) {
        pendingImport = importBooks
        start = .home
    }

    func importOpened() { pendingImport = false }

    /// Vuelve al onboarding (por ejemplo, para iniciar sesión desde el enlace de una contraseña nueva).
    func leaveHome() { start = .welcome }
}
