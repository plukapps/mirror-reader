import Foundation

/// Funciones que se prenden y apagan sin publicar una versión nueva, para todos o por plan (ACC-011, ADR 0016).
/// `rawValue` es el nombre del parámetro remoto. Como `Feature` de Android.
public enum Feature: String, CaseIterable, Sendable {
    /// SYN-017: sincronizar las notas.
    case notesSync = "notes_sync_enabled"
    /// GST-025: el banner sin cuenta de Inicio usa la opción B ("Tus libros sin respaldo").
    case guestBannerNoBackup = "guest_banner_no_backup_enabled"

    public var key: String { rawValue }

    /// Vale mientras no se sabe el valor remoto. SYN-017: si no se sabe, prendida. GST-025: si no se sabe, la A.
    public var defaultValue: Bool {
        switch self {
        case .notesSync: true
        case .guestBannerNoBackup: false
        }
    }
}

/// Números que se ajustan sin publicar una versión nueva, para afinarlos o probar valores (ADR 0016). Como `Setting`
/// de Android.
public enum Setting: String, CaseIterable, Sendable {
    /// GST-026: días que la cruz esconde el aviso sin cuenta (GST-010).
    case guestNoticeSnoozeDays = "guest_notice_snooze_days"

    public var key: String { rawValue }

    public var defaultValue: Int {
        switch self {
        case .guestNoticeSnoozeDays: 7
        }
    }
}

/// Valores remotos de las funciones (Remote Config en la app). Los tests usan un doble.
public protocol RemoteFlagSource: Sendable {
    /// Señal con el plan de la cuenta, para que las condiciones por plan elijan el valor (ADR 0016).
    func setPlan(_ plan: String) async throws
    /// Aplica lo último que se bajó.
    func activate() async throws
    /// Baja valores nuevos y los aplica. Falla sin conexión.
    func fetchAndActivate() async throws
    /// El valor aplicado de `key`, o nil si todavía no hay uno remoto.
    func value(_ key: String) async -> Bool?
    /// El número aplicado de `key`, o nil si todavía no hay uno remoto (o no es un número).
    func number(_ key: String) async -> Int?
    /// El catálogo de planes aplicado (`plans`), o nil si es inválido (ACC-007, ACC-008, ADR 0017).
    func plans() async -> [Plan]?
    /// Avisa cada vez que cambian los valores en el servidor, mientras se recorre.
    func updates() -> AsyncStream<Void>
}

extension RemoteFlagSource {
    public func number(_ key: String) async -> Int? { nil }
}

/// Funciones prendidas o apagadas según la configuración remota (ACC-011, SYN-017, ADR 0016), como
/// `RemoteFeatureFlags` de Android.
///
/// Arranca con los valores por defecto. Con la app a la vista ([startListening]): manda el plan de la cuenta, aplica
/// lo último bajado, pide lo nuevo y escucha los cambios del servidor, así apagar o prender llega en segundos. Sin
/// fuente (sin Firebase) quedan los valores por defecto.
public actor FeatureFlags {
    private let source: RemoteFlagSource?
    private let plan: @Sendable () -> String
    private let catalog: PlanCatalogStore
    private var values: [Feature: Bool] = Dictionary(uniqueKeysWithValues: Feature.allCases.map { ($0, $0.defaultValue) })
    private var settings: [Setting: Int] = Dictionary(uniqueKeysWithValues: Setting.allCases.map { ($0, $0.defaultValue) })
    private var subscribers: [UUID: (Feature, AsyncStream<Bool>.Continuation)] = [:]
    private var listening: Task<Void, Never>?

    /// - Parameters:
    ///   - plan: el plan de la cuenta (`users/{uid}.plan`), o `free` si no se sabe.
    ///   - catalog: dónde se publica el catálogo de planes que llega con la configuración (K-290).
    public init(
        source: RemoteFlagSource?,
        plan: @escaping @Sendable () -> String = { StorageQuota.freePlan },
        catalog: PlanCatalogStore = .shared
    ) {
        self.source = source
        self.plan = plan
        self.catalog = catalog
    }

    public func isEnabled(_ feature: Feature) -> Bool {
        values[feature] ?? feature.defaultValue
    }

    /// GST-026: el valor actual de `setting` (el de la app hasta que llega el remoto).
    public func setting(_ setting: Setting) -> Int {
        settings[setting] ?? setting.defaultValue
    }

    /// El valor actual de `feature` y cada cambio.
    public func changes(_ feature: Feature) -> AsyncStream<Bool> {
        let id = UUID()
        let (stream, continuation) = AsyncStream<Bool>.makeStream(bufferingPolicy: .bufferingNewest(1))
        continuation.yield(isEnabled(feature))
        subscribers[id] = (feature, continuation)
        continuation.onTermination = { [weak self] _ in Task { await self?.unsubscribe(id) } }
        return stream
    }

    /// Llamarlo otra vez no hace nada.
    public func startListening() {
        guard let source, listening == nil else { return }
        let plan = plan()
        listening = Task { [weak self] in
            try? await source.setPlan(plan)
            try? await source.activate()
            await self?.publish()
            // Sin conexión quedan los últimos valores conocidos (o los de por defecto).
            try? await source.fetchAndActivate()
            await self?.publish()
            for await _ in source.updates() {
                try? await source.activate()
                await self?.publish()
            }
        }
    }

    public func stopListening() {
        listening?.cancel()
        listening = nil
    }

    private func publish() async {
        guard let source else { return }
        for feature in Feature.allCases {
            let value = await source.value(feature.key) ?? feature.defaultValue
            guard values[feature] != value else { continue }
            values[feature] = value
            for (wanted, continuation) in subscribers.values where wanted == feature {
                continuation.yield(value)
            }
        }
        for setting in Setting.allCases {
            settings[setting] = await source.number(setting.key) ?? setting.defaultValue
        }
        // ACC-007, ACC-008, K-290: el catálogo de planes viaja con la misma configuración; inválido, los por defecto.
        catalog.update(await source.plans())
    }

    private func unsubscribe(_ id: UUID) {
        subscribers[id] = nil
    }
}
