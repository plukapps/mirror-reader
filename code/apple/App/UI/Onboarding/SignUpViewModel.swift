import Foundation
import Observation
import ReaderDomain

/// Crear cuenta, O2 (ONB-004 a ONB-006, ONB-020, ONB-021), como `SignUpViewModel` de Android.
@MainActor
@Observable
final class SignUpViewModel {
    enum Next: Equatable { case verifyEmail, interests }

    var name: String { didSet { error = nil } }
    var email: String { didSet { error = nil } }
    var password = "" { didSet { error = nil } }
    var passwordVisible = false
    var termsAccepted = false
    private(set) var busy = false
    private(set) var error: AuthError?
    /// Adónde ir; la vista navega y lo limpia con `onNavigated()`.
    private(set) var next: Next?

    @ObservationIgnored private let auth: AuthRepository

    /// ONB-008: al volver desde "Cambiar" llegan el nombre y el email, nunca la contraseña.
    init(auth: AuthRepository, name: String = "", email: String = "") {
        self.auth = auth
        self.name = name
        self.email = email
    }

    var passwordCheck: PasswordCheck { checkPassword(password) }

    /// ONB-004
    var canSubmit: Bool {
        !name.trimmingCharacters(in: .whitespaces).isEmpty && isValidEmail(email) && passwordCheck.isValid
            && termsAccepted && !busy
    }

    /// ONB-006
    func submit() async {
        guard canSubmit else { return }
        busy = true
        error = nil
        do {
            _ = try await auth.createAccount(name: name, email: email, password: password)
            // La contraseña no queda en memoria más de lo necesario.
            password = ""
            next = .verifyEmail
        } catch {
            self.error = error.authError
        }
        busy = false
    }

    /// ONB-006: con Google no hay que verificar el email. ONB-020: cancelar no muestra nada.
    func google(_ result: GoogleSignInResult) async {
        guard !busy else { return }
        switch result {
        case .cancelled: return
        case .failed: error = .unknown
        case let .tokens(tokens):
            busy = true
            error = nil
            do {
                _ = try await auth.signInWithGoogle(tokens)
                next = .interests
            } catch {
                self.error = error.authError
            }
            busy = false
        }
    }

    func onNavigated() { next = nil }
}
