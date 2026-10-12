import FirebaseCore
import FirebaseFirestore
import Foundation

/// Arranque de Firebase (ADR 0012). Sin `GoogleService-Info.plist` la app funciona solo en local (ADR 0002).
enum FirebaseSetup {
    /// Configura Firebase si está el archivo de configuración. Devuelve si quedó configurado.
    static func configureIfAvailable(bundle: Bundle = .main) -> Bool {
        guard bundle.url(forResource: "GoogleService-Info", withExtension: "plist") != nil else { return false }
        FirebaseApp.configure()
        // La base local manda (ADR 0002): sin caché en disco de Firestore, como en Android.
        let settings = Firestore.firestore().settings
        settings.cacheSettings = MemoryCacheSettings()
        Firestore.firestore().settings = settings
        return true
    }

    /// Dominio de las páginas de acción de Firebase Auth, al que apuntan los enlaces de los emails (ADR 0015).
    static func authDomain() -> String? {
        FirebaseApp.app()?.options.projectID.map { "\($0).firebaseapp.com" }
    }
}
