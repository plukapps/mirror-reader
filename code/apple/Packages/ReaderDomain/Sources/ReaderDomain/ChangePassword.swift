import Foundation

/// Cambio de contraseña de la cuenta con sesión iniciada (PRF-020).
public protocol PasswordRepository: Sendable {
    /// Verifica `current` y, si es correcta, la reemplaza por `new`. Falla con `AuthFailure`: una contraseña actual
    /// incorrecta es `.invalidCredentials`. La sesión de este dispositivo sigue abierta.
    func changePassword(current: String, new: String) async throws
}

/// PRF-020: "Guardar" se habilita con la actual no vacía, la nueva válida (ONB-005), distinta de la actual e igual a la
/// confirmación. Como `canSaveNewPassword` de Android.
public func canSaveNewPassword(current: String, new: String, confirm: String) -> Bool {
    !current.isEmpty && checkPassword(new).isValid && new != current && new == confirm
}
