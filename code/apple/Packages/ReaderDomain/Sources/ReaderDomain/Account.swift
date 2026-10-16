import Foundation

/// Cómo entra la cuenta. Define si se puede cambiar la contraseña desde Perfil (PRF-008).
public enum SignInMethod: Sendable {
    case password, google, other
}

/// Usuario con sesión iniciada. `id` es el `uid` del backend (ACC-004). `needsEmailVerification` es verdadero para una
/// cuenta de email que todavía no confirmó su dirección (ONB-019).
public struct AccountUser: Equatable, Sendable {
    public let id: String
    public let email: String?
    public let displayName: String?
    public let needsEmailVerification: Bool
    public let signInMethod: SignInMethod

    public init(
        id: String, email: String?, displayName: String? = nil, needsEmailVerification: Bool = false,
        signInMethod: SignInMethod = .password
    ) {
        self.id = id
        self.email = email
        self.displayName = displayName
        self.needsEmailVerification = needsEmailVerification
        self.signInMethod = signInMethod
    }
}

extension AccountUser {
    /// PRF-005, HOM-012: la inicial del nombre o, si no hay, del email, en mayúscula. Vacía si no hay ninguno.
    public var initial: String {
        (displayName ?? email)?.trimmingCharacters(in: .whitespaces).first.map { String($0).uppercased() } ?? ""
    }
}

public protocol AccountRepository: Sendable {
    /// Usuario actual, o nil si no hay sesión. Lo que el dispositivo ya conoce, sin red.
    func currentUser() async -> AccountUser?
}

/// Por qué falló una operación de cuenta, sin nombrar el backend (ONB-015, ADR 0015). Los mismos casos que Android.
public enum AuthError: Equatable, Sendable {
    /// Email o contraseña incorrectos. No distingue si el email tiene cuenta (ONB-015).
    case invalidCredentials
    case emailInUse
    case userDisabled
    case tooManyRequests
    case network
    /// El enlace para cambiar la contraseña venció o ya se usó (ONB-018).
    case invalidLink
    case weakPassword
    case unknown
}

/// Falla de `AuthRepository`.
public struct AuthFailure: Error, Equatable {
    public let error: AuthError

    public init(_ error: AuthError) {
        self.error = error
    }
}

extension Error {
    /// El `AuthError` de una falla de `AuthRepository`; cualquier otro error es `.unknown`.
    public var authError: AuthError { (self as? AuthFailure)?.error ?? .unknown }
}

/// Lo que entrega Google al elegir una cuenta (ONB-004, ONB-014).
public struct GoogleTokens: Equatable, Sendable {
    public let idToken: String
    public let accessToken: String

    public init(idToken: String, accessToken: String) {
        self.idToken = idToken
        self.accessToken = accessToken
    }
}

/// Alta, ingreso, verificación y recuperación de la cuenta (ONB, ADR 0015). Los métodos fallan con `AuthFailure`.
/// La sesión que resulta se lee en `AccountRepository.currentUser()`.
public protocol AuthRepository: Sendable {
    /// Crea la cuenta con email, guarda el nombre y envía el email de verificación (ONB-006).
    func createAccount(name: String, email: String, password: String) async throws -> AccountUser
    func signInWithPassword(email: String, password: String) async throws -> AccountUser
    /// Inicia sesión (o crea la cuenta) con la cuenta de Google elegida (ONB-004, ONB-014).
    func signInWithGoogle(_ tokens: GoogleTokens) async throws -> AccountUser
    func sendEmailVerification() async throws
    /// Vuelve a preguntar al servidor si el email del usuario actual ya se verificó (ONB-007).
    func refreshEmailVerified() async throws -> Bool
    /// Borra la cuenta actual si su email no está verificado (ONB-008).
    func discardUnverifiedAccount() async throws
    /// Envía el enlace para elegir una contraseña nueva (ONB-017). Responde igual exista o no la cuenta.
    func sendPasswordReset(email: String) async throws
    /// Email de la cuenta a la que pertenece el enlace, o `AuthError.invalidLink` (ONB-018).
    func emailForResetCode(_ code: String) async throws -> String
    func confirmPasswordReset(code: String, newPassword: String) async throws
    /// Aplica el enlace de verificación cuando lo abre la app en lugar del navegador (ONB-007).
    func applyEmailVerification(code: String) async throws
}

/// Enlace de acción de un email de la cuenta, abierto en la app (ONB-007, ONB-018, ADR 0015).
public enum AuthLink: Equatable, Sendable {
    case resetPassword(code: String)
    case verifyEmail(code: String)
}

/// Lee un enlace `https://<host>/__/auth/action?mode=…&oobCode=…`. Devuelve nil si no es un enlace de acción de `host`
/// o si su modo no se maneja en la app.
public func parseAuthLink(_ url: URL, host: String) -> AuthLink? {
    guard
        let components = URLComponents(url: url, resolvingAgainstBaseURL: false),
        components.scheme == "https",
        components.host?.lowercased() == host.lowercased(),
        components.path == "/__/auth/action"
    else { return nil }
    let items = components.queryItems ?? []
    func value(_ name: String) -> String? { items.first { $0.name == name }?.value }
    guard let code = value("oobCode"), !code.trimmingCharacters(in: .whitespaces).isEmpty else { return nil }
    switch value("mode") {
    case "resetPassword": return .resetPassword(code: code)
    case "verifyEmail": return .verifyEmail(code: code)
    default: return nil
    }
}
