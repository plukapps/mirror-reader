/// Usuario con sesión iniciada. `id` es el `uid` del backend (ACC-004).
public struct AccountUser: Equatable, Sendable {
    public let id: String
    public let email: String?
    public init(id: String, email: String?) {
        self.id = id
        self.email = email
    }
}

public protocol AccountRepository: Sendable {
    /// Usuario actual, o nil si no hay sesión.
    func currentUser() async -> AccountUser?
    /// Inicia sesión con email y contraseña (ACC-001).
    func signIn(email: String, password: String) async throws -> AccountUser
}

/// Cuenta de desarrollo mientras no existan las pantallas de cuenta (como Android, K-052). Solo en Debug.
public struct DevCredentials: Sendable, CustomStringConvertible {
    public let email: String
    public let password: String

    public init(email: String, password: String) {
        self.email = email
        self.password = password
    }

    public var isConfigured: Bool { !email.isEmpty && !password.isEmpty }

    // La contraseña nunca debe llegar a un log por una interpolación accidental.
    public var description: String { "DevCredentials(email: \(email), password: ***)" }
}
