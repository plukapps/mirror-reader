import FirebaseAuth
import FirebaseCrashlytics
import ReaderDomain

/// Cierres inesperados en Firebase Crashlytics (CMP-010, ADR 0018).
@MainActor
final class FirebaseCrashReporter: CrashReporter {
    func setUserID(_ id: String) {
        Crashlytics.crashlytics().setUserID(id)
    }
}

/// CMP-010: sigue la sesión de Firebase Auth para que los reportes lleven el `uid`, o nada sin sesión.
@MainActor
enum CrashReportSession {
    /// Solo con Firebase configurado. El oyente queda registrado mientras viva la app.
    static func start() {
        let identity = CrashReportIdentity(reporter: FirebaseCrashReporter())
        _ = Auth.auth().addStateDidChangeListener { _, user in
            // Firebase Auth llama a sus oyentes en el hilo principal.
            let uid = user?.uid
            MainActor.assumeIsolated { identity.update(userId: uid) }
        }
    }
}
