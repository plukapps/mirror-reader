import Foundation
import ReaderDomain

/// Entorno y versión de este build (APL-002, ADR 0021), leídos del `Info.plist`: la configuración de build escribe
/// `AppEnvironment` (dev, stage, beta o prod).
enum BuildEnvironment {
    /// Un valor desconocido detiene la app: una configuración mal armada no debe correr como otro entorno.
    static func current(bundle: Bundle = .main) -> AppEnvironment {
        let id = bundle.object(forInfoDictionaryKey: "AppEnvironment") as? String ?? ""
        guard let environment = AppEnvironment(id: id) else {
            fatalError("AppEnvironment desconocido en el Info.plist: '\(id)' (ver project.yml, ADR 0021)")
        }
        return environment
    }

    /// La versión de la app, con el entorno fuera de producción (por ejemplo `0.1.0 · dev`).
    static func versionLabel(bundle: Bundle = .main) -> String {
        let version = bundle.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? ""
        return current(bundle: bundle).versionLabel(version)
    }
}
