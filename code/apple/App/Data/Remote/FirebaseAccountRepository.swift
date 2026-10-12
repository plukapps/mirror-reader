import FirebaseAuth
import Foundation
import OSLog
import ReaderDomain

/// Sesión y cuenta con Firebase Auth: email y contraseña, y Google (ADR 0012, ADR 0015), como
/// `FirebaseAccountRepository` de Android. Firebase guarda la sesión entre arranques.
struct FirebaseAccountRepository: AccountRepository, AuthRepository {
    private var auth: Auth { Auth.auth() }

    func currentUser() async -> AccountUser? {
        auth.currentUser.map(accountUser)
    }

    func signInWithPassword(email: String, password: String) async throws -> AccountUser {
        try await run("iniciar sesión") {
            accountUser(try await auth.signIn(withEmail: email.trimmed, password: password).user)
        }
    }

    func createAccount(name: String, email: String, password: String) async throws -> AccountUser {
        try await run("crear la cuenta") {
            let created = try await auth.createUser(withEmail: email.trimmed, password: password).user
            let profile = created.createProfileChangeRequest()
            profile.displayName = name.trimmed
            try await profile.commitChanges()
            // Si el envío falla, la cuenta ya existe: O3 permite reenviarlo (ONB-007).
            try? await created.sendEmailVerification()
            return accountUser(created)
        }
    }

    func signInWithGoogle(_ tokens: GoogleTokens) async throws -> AccountUser {
        try await run("iniciar sesión con Google") {
            let credential = GoogleAuthProvider.credential(withIDToken: tokens.idToken, accessToken: tokens.accessToken)
            return accountUser(try await auth.signIn(with: credential).user)
        }
    }

    func sendEmailVerification() async throws {
        try await run("enviar la verificación") { try await requiredUser().sendEmailVerification() }
    }

    func refreshEmailVerified() async throws -> Bool {
        try await run("consultar la verificación") {
            let user = try requiredUser()
            try await user.reload()
            let verified = auth.currentUser?.isEmailVerified == true
            // El token nuevo lleva email_verified (lo usarán las reglas, K-050).
            if verified { _ = try await user.getIDToken(forcingRefresh: true) }
            return verified
        }
    }

    func discardUnverifiedAccount() async throws {
        try await run("descartar la cuenta") {
            if let user = auth.currentUser, accountUser(user).needsEmailVerification { try await user.delete() }
        }
    }

    func sendPasswordReset(email: String) async throws {
        do {
            try await run("enviar el enlace") { try await auth.sendPasswordReset(withEmail: email.trimmed) }
        } catch let failure as AuthFailure where failure.error == .invalidCredentials {
            // ONB-017: se responde igual exista o no la cuenta.
        }
    }

    func emailForResetCode(_ code: String) async throws -> String {
        try await run("leer el enlace") { try await auth.verifyPasswordResetCode(code) }
    }

    func confirmPasswordReset(code: String, newPassword: String) async throws {
        try await run("cambiar la contraseña") { try await auth.confirmPasswordReset(withCode: code, newPassword: newPassword) }
    }

    func applyEmailVerification(code: String) async throws {
        try await run("aplicar la verificación") {
            try await auth.applyActionCode(code)
            // Si es el usuario de esta sesión, que se entere ya (ONB-007).
            if let user = auth.currentUser {
                try await user.reload()
                _ = try await user.getIDToken(forcingRefresh: true)
            }
        }
    }

    private func requiredUser() throws -> User {
        guard let user = auth.currentUser else { throw AuthFailure(.unknown) }
        return user
    }

    /// Traduce cualquier error del SDK a `AuthFailure` y lo deja en el log, sin datos de la cuenta.
    private func run<T>(_ action: String, _ block: () async throws -> T) async throws -> T {
        do {
            return try await block()
        } catch let failure as AuthFailure {
            throw failure
        } catch {
            Logger(subsystem: "com.pluk.reader", category: "Account")
                .error("No se pudo \(action, privacy: .public): \(String(describing: error), privacy: .public)")
            throw AuthFailure(authError(error))
        }
    }

    private func accountUser(_ user: User) -> AccountUser {
        let providers = user.providerData.map(\.providerID)
        let name = user.displayName?.trimmed
        return AccountUser(
            id: user.uid,
            email: user.email,
            displayName: name?.isEmpty == false ? name : nil,
            // Solo las cuentas de email verifican; Google ya llega verificada (ONB-006).
            needsEmailVerification: !user.isEmailVerified
                && providers.contains(EmailAuthProvider.id)
                && !providers.contains(GoogleAuthProvider.id)
        )
    }
}

/// Traduce los errores de Firebase Auth a `AuthError` (ONB-015, ADR 0015), como `AuthErrors.kt` de Android.
func authError(_ error: Error) -> AuthError {
    let nsError = error as NSError
    if nsError.domain == NSURLErrorDomain { return .network }
    guard nsError.domain == AuthErrors.domain, let code = AuthErrorCode(rawValue: nsError.code) else { return .unknown }
    switch code {
    // Sin la protección contra enumeración, Firebase avisa si el email no existe: se muestra igual que una contraseña
    // equivocada para no revelarlo (ONB-015).
    case .wrongPassword, .invalidCredential, .userNotFound, .invalidEmail: return .invalidCredentials
    case .emailAlreadyInUse, .credentialAlreadyInUse, .accountExistsWithDifferentCredential: return .emailInUse
    case .userDisabled: return .userDisabled
    case .tooManyRequests: return .tooManyRequests
    case .networkError: return .network
    case .weakPassword: return .weakPassword
    case .invalidActionCode, .expiredActionCode: return .invalidLink
    default: return .unknown
    }
}

private extension String {
    var trimmed: String { trimmingCharacters(in: .whitespacesAndNewlines) }
}
