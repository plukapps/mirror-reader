import FirebaseCore
import FirebaseFirestore
import Foundation
import ReaderDomain

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

    /// Cuenta de desarrollo del `Info.plist` (vacía en Release o si no se configuró, ver `Signing.xcconfig`).
    static func devCredentials(bundle: Bundle = .main) -> DevCredentials {
        func value(_ key: String) -> String {
            let raw = (bundle.object(forInfoDictionaryKey: key) as? String) ?? ""
            // Una variable sin definir puede quedar literal: "$(DEV_ACCOUNT_EMAIL)".
            return raw.hasPrefix("$(") ? "" : raw.trimmingCharacters(in: .whitespaces)
        }
        return DevCredentials(email: value("DevAccountEmail"), password: value("DevAccountPassword"))
    }
}
