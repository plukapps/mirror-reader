import Foundation
import Observation
import ReaderDomain

/// Iniciar sesión, O7 (ONB-014 a ONB-016, ONB-020, ONB-021), como `SignInViewModel` de Android.
@MainActor
@Observable
final class SignInViewModel {
    enum Next: Equatable { case home, verifyEmail }

    var email = "" { didSet { error = nil } }
    var password = "" { didSet { error = nil } }
    var passwordVisible = false
    private(set) var busy = false
    private(set) var error: AuthError?
    private(set) var next: Next?
    /// Si salió el email de verificación al ingresar (ONB-016): O3 arranca esperando para reenviar.
    private(set) var verificationSent = false

    @ObservationIgnored private let auth: AuthRepository

    init(auth: AuthRepository) {
        self.auth = auth
    }

    var canSubmit: Bool { isValidEmail(email) && !password.isEmpty && !busy }

    func submit() async {
        guard canSubmit else { return }
        busy = true
        error = nil
        do {
            let user = try await auth.signInWithPassword(email: email, password: password)
            // ONB-016: la cuenta pudo crearse en otro lado sin verificar: el email sale ahora.
            if user.needsEmailVerification {
                verificationSent = (try? await auth.sendEmailVerification()) != nil
            }
            password = ""
            next = user.needsEmailVerification ? .verifyEmail : .home
        } catch {
            self.error = error.authError
        }
        busy = false
    }

    /// ONB-020: cancelar no muestra nada; un fallo, sí.
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
                next = .home
            } catch {
                self.error = error.authError
            }
            busy = false
        }
    }

    func onNavigated() { next = nil }
}

/// Olvidé mi contraseña, O8 (ONB-017), como `ForgotPasswordViewModel` de Android.
@MainActor
@Observable
final class ForgotPasswordViewModel {
    static let resendSeconds = 30

    var email: String { didSet { error = nil } }
    private(set) var busy = false
    private(set) var sent = false
    private(set) var resendSeconds = 0
    private(set) var error: AuthError?

    @ObservationIgnored private let auth: AuthRepository
    @ObservationIgnored private let second: Duration
    @ObservationIgnored private var cooldown: Task<Void, Never>?

    /// - Parameter email: el que se escribió en O7.
    init(auth: AuthRepository, email: String, second: Duration = .seconds(1)) {
        self.auth = auth
        self.email = email
        self.second = second
    }

    var canSend: Bool { isValidEmail(email) && !busy && resendSeconds == 0 }

    /// Enviar y reenviar.
    func send() async {
        guard canSend else { return }
        busy = true
        error = nil
        do {
            try await auth.sendPasswordReset(email: email)
            sent = true
            cooldown?.cancel()
            cooldown = countdown(Self.resendSeconds, every: second) { [weak self] left in self?.resendSeconds = left }
        } catch {
            self.error = error.authError
        }
        busy = false
    }
}

/// Nueva contraseña desde el enlace del email, O9 (ONB-018), como `ResetPasswordViewModel` de Android.
@MainActor
@Observable
final class ResetPasswordViewModel {
    enum Next: Equatable { case home, signIn }

    private(set) var loading = true
    private(set) var email = ""
    private(set) var linkInvalid = false
    var password = "" { didSet { error = nil } }
    var confirm = "" { didSet { error = nil } }
    var passwordVisible = false
    private(set) var busy = false
    private(set) var error: AuthError?
    private(set) var next: Next?

    @ObservationIgnored private let auth: AuthRepository
    @ObservationIgnored private let code: String

    init(auth: AuthRepository, code: String) {
        self.auth = auth
        self.code = code
    }

    var passwordCheck: PasswordCheck { checkPassword(password) }
    var mismatch: Bool { !confirm.isEmpty && confirm != password }
    var canSave: Bool { !loading && !linkInvalid && passwordCheck.isValid && password == confirm && !busy }

    /// Averigua de qué cuenta es el enlace; si venció o ya se usó, lo dice.
    func load() async {
        let found = code.isEmpty ? nil : try? await auth.emailForResetCode(code)
        email = found ?? ""
        linkInvalid = found == nil
        loading = false
    }

    /// "Guardar e ingresar". Si la contraseña cambia pero el ingreso falla, se va a iniciar sesión.
    func save() async {
        guard canSave else { return }
        busy = true
        error = nil
        defer { busy = false }
        do {
            try await auth.confirmPasswordReset(code: code, newPassword: password)
        } catch {
            let failure = error.authError
            self.error = failure
            linkInvalid = failure == .invalidLink
            return
        }
        let signedIn = (try? await auth.signInWithPassword(email: email, password: password)) != nil
        password = ""
        confirm = ""
        next = signedIn ? .home : .signIn
    }

    func onNavigated() { next = nil }
}
