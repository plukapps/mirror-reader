import Foundation
import ReaderDomain

/// Cambiar contraseña desde Perfil (PRF-020), como `ChangePasswordViewModel` de Android.
@MainActor
@Observable
final class ChangePasswordViewModel {
    var current = "" { didSet { currentError = nil; error = nil } }
    var password = "" { didSet { error = nil } }
    var confirm = "" { didSet { error = nil } }
    var currentVisible = false
    /// La nueva y la confirmación se ven u ocultan juntas, como en O9.
    var passwordVisible = false
    private(set) var busy = false
    /// Aviso bajo "Contraseña actual" cuando el servidor dice que no es correcta.
    private(set) var currentError: String?
    /// Cualquier otro error (sin conexión, demasiados intentos), con los textos de ONB-015.
    private(set) var error: AuthError?
    /// La contraseña cambió: la pantalla vuelve a Perfil con el aviso.
    private(set) var saved = false

    @ObservationIgnored private let passwords: PasswordRepository

    init(passwords: PasswordRepository) {
        self.passwords = passwords
    }

    var passwordCheck: PasswordCheck { checkPassword(password) }
    var mismatch: Bool { !confirm.isEmpty && confirm != password }
    var canSave: Bool { !busy && canSaveNewPassword(current: current, new: password, confirm: confirm) }

    func save() async {
        guard canSave else { return }
        busy = true
        defer { busy = false }
        do {
            try await passwords.changePassword(current: current, new: password)
            saved = true
        } catch {
            let failure = error.authError
            if failure == .invalidCredentials {
                currentError = String(localized: "La contraseña actual no es correcta.")
            } else {
                self.error = failure
            }
        }
    }
}
