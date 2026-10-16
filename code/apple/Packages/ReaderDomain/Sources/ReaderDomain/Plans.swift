import Foundation

/// Planes de suscripción (PLN-001), como `PlanId` de Android. Free, Plus y Pro; el `rawValue` es el id del backend.
public enum PlanId: String, CaseIterable, Sendable {
    case free, plus, pro
}

/// Forma de pago elegida en Planes (PLN-004).
public enum Billing: Sendable {
    case monthly, annual
}

/// Un plan del catálogo (ACC-007): espacio, máximo por archivo y precios, sin textos (los pone la app, K-295). En Free
/// la app muestra la cuota de la cuenta si la cuenta está en Free (PLN-001, `PlanCatalog.storageBytes`). Los precios son
/// informativos (open-questions #1): la rebanada de compras los toma de la tienda.
public struct Plan: Equatable, Sendable {
    public let id: PlanId
    public let storageBytes: Int64
    /// PLN-014: tamaño máximo de cada archivo que se sube a la nube. Vale mientras no se conoce el del servidor (ACC-008).
    public let maxFileBytes: Int64
    public let monthlyPrice: Double
    public let annualPrice: Double
    public let currency: String

    public init(id: PlanId, storageBytes: Int64, maxFileBytes: Int64, monthlyPrice: Double, annualPrice: Double, currency: String) {
        self.id = id
        self.storageBytes = storageBytes
        self.maxFileBytes = maxFileBytes
        self.monthlyPrice = monthlyPrice
        self.annualPrice = annualPrice
        self.currency = currency
    }

    /// PLN-001: "$2.99" según la forma de pago.
    public func price(_ billing: Billing) -> String {
        formatPrice(billing == .annual ? annualPrice : monthlyPrice)
    }

    /// Equivalencia por mes del precio anual: el anual dividido 12, redondeado al centavo ("$2.08").
    public var annualPerMonth: String { formatPrice((annualPrice / 12 * 100).rounded() / 100) }
}

/// PLN-001: precio fijo con "$" y dos decimales ("$2.99"); sin costo, "$0". No usa el formato del país del dispositivo
/// (decisión del usuario, 2026-10-14): el precio real llega de la tienda con el cobro.
public func formatPrice(_ value: Double) -> String {
    value == 0 ? "$0" : "$" + String(format: "%.2f", locale: Locale(identifier: "en_US_POSIX"), value)
}

/// PLN-003: lo usado y el total con la unidad y el formato de PRF-006, la proporción y la barra. El texto ("Usás X de
/// Y") lo arma la app.
public struct PlanUsage: Equatable, Sendable {
    public let used: String
    public let total: String
    public let free: String
    /// Entre 0 y 1.
    public let fraction: Double

    /// nil sin cuota: nunca se leyó y la fila no se muestra.
    public init?(_ quota: StorageQuota?) {
        guard let quota else { return nil }
        let unit = StorageUnit.forTotal(quota.quotaBytes)
        used = formatStorage(quota.usedBytes, unit: unit)
        total = formatStorage(quota.quotaBytes, unit: unit)
        free = formatStorage(quota.freeBytes, unit: unit)
        fraction = quota.quotaBytes <= 0 ? 1 : min(max(Double(quota.usedBytes) / Double(quota.quotaBytes), 0), 1)
    }

    /// La proporción en por ciento, redondeada (el "80 %" de la derecha).
    public var percent: Int { Int((fraction * 100).rounded()) }
}

/// Reglas de los planes sobre la cuenta (PLN-001), con el catálogo vigente: el de Remote Config si es válido, si no
/// `DefaultPlans` (ACC-007, ACC-008, ADR 0017). Cada regla acepta un catálogo propio (`in:`), para los `ViewModel` que
/// siguen sus cambios; sin él, usa el vigente.
public enum PlanCatalog {
    public static let annualSavingPercent = 30

    /// Solo para tests: un catálogo propio de la tarea, sin tocar el compartido (Swift Testing corre en paralelo).
    @TaskLocal public static var override: [Plan]?

    /// El catálogo vigente. Siempre tiene Free, Plus y Pro, en ese orden.
    public static var all: [Plan] { override ?? PlanCatalogStore.shared.plans }

    public static func plan(_ id: PlanId, in catalog: [Plan]? = nil) -> Plan {
        (catalog ?? all).first { $0.id == id } ?? DefaultPlans.all.first { $0.id == id }!
    }

    /// PLN-001: espacio del plan; el de Free es la cuota de la cuenta si la cuenta está en Free.
    public static func storageBytes(_ id: PlanId, quota: StorageQuota?, in catalog: [Plan]? = nil) -> Int64 {
        // En una cuenta paga, Free no es la cuota de la cuenta sino la del catálogo (K-291).
        if id == .free, let quota, quota.isFreePlan { return quota.quotaBytes }
        return plan(id, in: catalog).storageBytes
    }

    /// "15 MB", "5 GB": con la unidad de su propio total (PRF-006).
    public static func storageText(_ id: PlanId, quota: StorageQuota?, in catalog: [Plan]? = nil) -> String {
        let bytes = storageBytes(id, quota: quota, in: catalog)
        return formatStorage(bytes, unit: StorageUnit.forTotal(bytes))
    }

    /// PLN-014: el plan de la cuenta sale de la cuota (el `rawValue` es el id de `users/{uid}.plan` y del catálogo,
    /// ACC-007, ADR 0017); si no se conoce o no es uno de los tres, Free.
    public static func planOf(_ quota: StorageQuota?) -> PlanId {
        quota.flatMap { PlanId(rawValue: $0.plan) } ?? .free
    }

    /// PLN-014, ACC-008: tamaño máximo por archivo de la cuenta; el del servidor si se conoce, si no el fijo del plan.
    public static func maxFileBytes(_ quota: StorageQuota?, in catalog: [Plan]? = nil) -> Int64 {
        quota?.maxFileBytes ?? plan(planOf(quota), in: catalog).maxFileBytes
    }

    /// PLN-014: el plan que se ofrece para pasar de este ("Con Plus…", "Con Pro…"); nil en Pro.
    public static func nextPlan(after id: PlanId) -> PlanId? {
        switch id {
        case .free: .plus
        case .plus: .pro
        case .pro: nil
        }
    }

    /// "2 MB", "50 MB": el tamaño máximo por archivo del plan (PLN-014).
    public static func maxFileText(_ id: PlanId, in catalog: [Plan]? = nil) -> String {
        let bytes = plan(id, in: catalog).maxFileBytes
        return formatStorage(bytes, unit: StorageUnit.forTotal(bytes))
    }
}

// MARK: Temas y fuentes (PLN-015)

public extension PlanId {
    /// PLN-015: temas y fuentes son de Plus y Pro.
    var hasThemesAndFonts: Bool { self != .free }
}

public extension ReadingTheme {
    /// PLN-015: Sepia y Noche son de los planes pagos; Clásico es de todos.
    var isPaid: Bool { self != .light }
}

public extension ReaderFont {
    /// PLN-015: sin serifa y monoespaciada son de los planes pagos; la serifa es de todos.
    var isPaid: Bool { self != .serif }
}

/// PLN-015: lo que el lector aplica. En Free, Clásico y serifa; el resto de lo guardado (tamaño, interlineado) sigue
/// igual. La preferencia guardada no se toca: vuelve si la cuenta pasa a un plan pago.
public func effectiveReaderSettings(_ saved: ReaderSettings, plan: PlanId) -> ReaderSettings {
    guard !plan.hasThemesAndFonts else { return saved }
    return saved.withTheme(.light).withFont(.serif)
}
