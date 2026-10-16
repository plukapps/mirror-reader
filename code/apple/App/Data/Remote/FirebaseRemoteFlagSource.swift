import FirebaseRemoteConfig
import Foundation
import ReaderDomain

/// Remote Config (ADR 0016, ADR 0017), como `FirebaseRemoteFlagSource` de Android. Los valores por defecto de la app son
/// los de `Feature` y `DefaultPlans`; un parámetro que no existe en el proyecto vale su valor por defecto.
struct FirebaseRemoteFlagSource: RemoteFlagSource {
    /// Señal personalizada con el plan de la cuenta (`users/{uid}.plan`).
    private static let planSignal = "plan"

    private var config: RemoteConfig {
        let config = RemoteConfig.remoteConfig()
        // Todos los valores por defecto juntos: cada llamada reemplaza los anteriores (ADR 0017).
        var defaults: [String: NSObject] = Dictionary(uniqueKeysWithValues: Feature.allCases.map { ($0.key, NSNumber(value: $0.defaultValue)) })
        for setting in Setting.allCases { defaults[setting.key] = NSNumber(value: setting.defaultValue) }
        defaults[PlanCatalogJSON.key] = NSString(string: PlanCatalogJSON.json(DefaultPlans.all))
        config.setDefaults(defaults)
        return config
    }

    func setPlan(_ plan: String) async throws {
        try await config.setCustomSignals([Self.planSignal: CustomSignalValue.string(plan)])
    }

    func activate() async throws {
        _ = try await config.activate()
    }

    func fetchAndActivate() async throws {
        _ = try await config.fetchAndActivate()
    }

    func value(_ key: String) async -> Bool? {
        // Sin valor remoto ni por defecto la fuente es `.static`: se usa el de la app.
        let value = config.configValue(forKey: key)
        return value.source == .static ? nil : value.boolValue
    }

    func number(_ key: String) async -> Int? {
        let value = config.configValue(forKey: key)
        guard value.source != .static else { return nil }
        return Int(value.stringValue.trimmingCharacters(in: .whitespaces))
    }

    func plans() async -> [Plan]? {
        let text: String? = config.configValue(forKey: PlanCatalogJSON.key).stringValue
        return text.flatMap(PlanCatalogJSON.parse)
    }

    func updates() -> AsyncStream<Void> {
        let config = config
        return AsyncStream { continuation in
            // Sin conexión el listener reintenta solo; los valores conocidos siguen valiendo.
            let registration = config.addOnConfigUpdateListener { update, error in
                if update != nil, error == nil { continuation.yield() }
            }
            continuation.onTermination = { _ in registration.remove() }
        }
    }
}
