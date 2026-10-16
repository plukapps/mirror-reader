import Foundation
import Observation
import ReaderDomain

/// Pantalla con la que abre la app (GST-003), como `MainViewModel.start` de Android.
@MainActor
@Observable
final class AppStartViewModel {
    enum Start: Equatable { case loading, welcome, verifyEmail, home }

    private(set) var start: Start = .loading
    /// ONB-013: "Importar mis libros" abre el selector apenas aparece Inicio.
    private(set) var pendingImport = false
    /// GST-021, GST-022: al terminar el alta corta o el ingreso desde adentro, se abre la Biblioteca.
    private(set) var pendingLibrary = false

    /// nil si la app funciona solo en local (sin Firebase): no hay cuenta que crear y abre Inicio (ADR 0015).
    @ObservationIgnored private let account: AccountRepository?
    @ObservationIgnored private let welcome: WelcomePreference
    @ObservationIgnored private var decided = false

    init(account: AccountRepository?, welcome: WelcomePreference) {
        self.account = account
        self.welcome = welcome
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
        start = switch startDestination(await account.currentUser(), welcomeSeen: welcome.seen()) {
        case .welcome: .welcome
        case .verifyEmail: .verifyEmail
        case .home: .home
        }
    }

    /// GST-002: "Empezar a leer" registra que ya se pasó por la bienvenida.
    func markWelcomeSeen() { welcome.markSeen() }

    /// Fin del onboarding: a Inicio, y atrás ya no vuelve (ONB-013, ONB-016).
    func enterHome(importBooks: Bool, openLibrary: Bool = false) {
        pendingImport = importBooks
        pendingLibrary = openLibrary
        start = .home
    }

    func importOpened() { pendingImport = false }

    func libraryOpened() { pendingLibrary = false }

    /// Vuelve al onboarding (por ejemplo, para iniciar sesión desde el enlace de una contraseña nueva).
    func leaveHome() { start = .welcome }
}
