import FirebaseAuth
import OSLog
import ReaderDomain

/// Sesión con Firebase Auth (ACC-001). Firebase guarda la sesión entre arranques.
struct FirebaseAccountRepository: AccountRepository {
    func currentUser() async -> AccountUser? {
        Auth.auth().currentUser.map { AccountUser(id: $0.uid, email: $0.email) }
    }

    func signIn(email: String, password: String) async throws -> AccountUser {
        do {
            let user = try await Auth.auth().signIn(withEmail: email, password: password).user
            return AccountUser(id: user.uid, email: user.email)
        } catch {
            Logger(subsystem: "com.pluk.reader", category: "Account")
                .error("No se pudo iniciar sesión: \(String(describing: error), privacy: .public)")
            throw remoteError(error)
        }
    }
}
