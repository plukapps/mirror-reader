import FirebaseRemoteConfig
import Foundation
import ReaderDomain

/// Remote Config (ADR 0016), como `FirebaseRemoteFlagSource` de Android. Los valores por defecto de la app son los de
/// `Feature`; un parámetro que no existe en el proyecto vale su valor por defecto.
struct FirebaseRemoteFlagSource: RemoteFlagSource {
    /// Señal personalizada con el plan de la cuenta (`users/{uid}.plan`).
    private static let planSignal = "plan"

    private var config: RemoteConfig {
        let config = RemoteConfig.remoteConfig()
        config.setDefaults(Dictionary(uniqueKeysWithValues: Feature.allCases.map { ($0.key, NSNumber(value: $0.defaultValue)) }))
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
