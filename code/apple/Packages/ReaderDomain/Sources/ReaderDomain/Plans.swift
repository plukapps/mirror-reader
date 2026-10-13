import Foundation

/// Planes de suscripción (PLN-001), como `PlanId` de Android.
public enum PlanId: String, CaseIterable, Sendable {
    case free, reader, library
}

/// Forma de pago elegida en Planes (PLN-004).
public enum Billing: Sendable {
    case monthly, annual

    /// "por año" / "por mes", junto al precio de cada tarjeta.
    public var perLabel: String {
        switch self {
        case .annual: "por año"
        case .monthly: "por mes"
        }
    }

    fileprivate var period: String {
        switch self {
        case .annual: "año"
        case .monthly: "mes"
        }
    }
}

/// Un plan del catálogo. `storageBytes` es nil en Gratis, que usa la cuota de la cuenta (ACC-002). Los precios son de
/// ejemplo y fijos (open-questions #1): la rebanada de compras los toma de la tienda.
public struct Plan: Equatable, Sendable {
    public let id: PlanId
    public let name: String
    public let tagline: String
    public let storageBytes: Int64?
    public let includes: [String]
    public let monthlyPrice: String
    public let annualPrice: String
    /// Equivalencia por mes del precio anual ("$2.08"), o nil en Gratis.
    public let annualPerMonth: String?

    public func price(_ billing: Billing) -> String {
        billing == .annual ? annualPrice : monthlyPrice
    }

    /// PLN-004: "Equivale a $2.08 por mes" en anual, "Pagás mes a mes" en mensual.
    public func periodNote(_ billing: Billing) -> String {
        switch billing {
        case .annual: annualPerMonth.map { "Equivale a \($0) por mes" } ?? ""
        case .monthly: "Pagás mes a mes"
        }
    }
}

/// Botón de compra de P01 y su texto de abajo (PLN-006).
public struct PlanCta: Equatable, Sendable {
    public let title: String
    public let subtitle: String

    public init(title: String, subtitle: String) {
        self.title = title
        self.subtitle = subtitle
    }
}

/// Fila de la tabla comparativa (PLN-006).
public struct PlanComparisonRow: Equatable, Sendable {
    public let feature: String
    public let free: String
    public let reader: String
    public let library: String

    public init(feature: String, free: String, reader: String, library: String) {
        self.feature = feature
        self.free = free
        self.reader = reader
        self.library = library
    }
}

/// PLN-003: "Usás X de Y" con la unidad y el formato de PRF-006, la proporción y la barra.
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

    public var text: String { "Usás \(used) de \(total)" }
    /// "80 %", en letra monoespaciada a la derecha.
    public var ratio: String { "\(Int((fraction * 100).rounded())) %" }
}

/// Catálogo de planes (PLN-001). Valores provisionales hasta definir precios (open-questions #1).
public enum PlanCatalog {
    private static let mib: Int64 = 1024 * 1024
    private static let gib: Int64 = 1024 * mib

    /// Cuota de Gratis de hoy (backend `FREE_PLAN`), para cuando nunca se leyó la de la cuenta.
    public static let freeQuotaBytes: Int64 = 15 * mib
    public static let trialDays = 14
    public static let annualSavingPercent = 30

    public static let free = Plan(
        id: .free, name: "Gratis", tagline: "Para leer de vez en cuando", storageBytes: nil,
        includes: ["Lectura y notas sin tope", "Sincroniza en todos tus dispositivos", "Estadísticas básicas"],
        monthlyPrice: "$0", annualPrice: "$0", annualPerMonth: nil
    )
    public static let reader = Plan(
        id: .reader, name: "Lector", tagline: "Para quien lee siempre", storageBytes: 5 * gib,
        includes: ["Exportá notas a Markdown, Obsidian y Notion", "Estadísticas y metas avanzadas", "Temas y fuentes propias"],
        monthlyPrice: "$2.99", annualPrice: "$24.99", annualPerMonth: "$2.08"
    )
    public static let library = Plan(
        id: .library, name: "Biblioteca", tagline: "Para quien tiene una biblioteca", storageBytes: 50 * gib,
        includes: ["Todo lo de Lector", "Importá desde Drive, Calibre o por email", "Familia: hasta 5 cuentas"],
        monthlyPrice: "$5.99", annualPrice: "$49.99", annualPerMonth: "$4.17"
    )

    public static let all = [free, reader, library]

    public static func plan(_ id: PlanId) -> Plan {
        switch id {
        case .free: free
        case .reader: reader
        case .library: library
        }
    }

    /// PLN-001: espacio del plan; el de Gratis es la cuota de la cuenta.
    public static func storageBytes(_ id: PlanId, quota: StorageQuota?) -> Int64 {
        plan(id).storageBytes ?? quota?.quotaBytes ?? freeQuotaBytes
    }

    /// "15 MB", "5 GB": con la unidad de su propio total (PRF-006).
    public static func storageText(_ id: PlanId, quota: StorageQuota?) -> String {
        let bytes = storageBytes(id, quota: quota)
        return formatStorage(bytes, unit: StorageUnit.forTotal(bytes))
    }

    /// PLN-006: en anual se prueba gratis; en mensual se elige con el precio por mes.
    public static func cta(_ id: PlanId, _ billing: Billing) -> PlanCta {
        let plan = plan(id)
        let price = plan.price(billing)
        return switch billing {
        case .annual: PlanCta(
                title: "Probar \(plan.name) \(trialDays) días gratis",
                subtitle: "Después \(price) por \(billing.period). Cancelás cuando quieras."
            )
        case .monthly: PlanCta(
                title: "Elegir \(plan.name) · \(price) por \(billing.period)",
                subtitle: "Se renueva cada \(billing.period). Cancelás cuando quieras."
            )
        }
    }

    /// PLN-006: filas de la tabla del diseño, con el espacio en lugar de libros y sin tope por archivo (PLN-001).
    public static func comparison(quota: StorageQuota?) -> [PlanComparisonRow] {
        let yes = "✓", no = "—"
        return [
            PlanComparisonRow(
                feature: "Espacio en la nube", free: storageText(.free, quota: quota),
                reader: storageText(.reader, quota: quota), library: storageText(.library, quota: quota)
            ),
            PlanComparisonRow(feature: "Lectura, ajustes y anotaciones", free: "Sin tope", reader: "Sin tope", library: "Sin tope"),
            PlanComparisonRow(feature: "Sincronización en todos tus dispositivos", free: yes, reader: yes, library: yes),
            PlanComparisonRow(feature: "Exportar notas (Markdown, Obsidian, Notion)", free: no, reader: yes, library: yes),
            PlanComparisonRow(feature: "Estadísticas y metas", free: "Básicas", reader: "Avanzadas", library: "Avanzadas"),
            PlanComparisonRow(feature: "Temas y fuentes propias", free: no, reader: yes, library: yes),
            PlanComparisonRow(feature: "Importar desde Drive, Calibre/OPDS o email", free: no, reader: no, library: yes),
            PlanComparisonRow(feature: "Lector web (próximamente)", free: no, reader: no, library: yes),
            PlanComparisonRow(feature: "Familia (hasta 5 cuentas)", free: no, reader: no, library: yes),
        ]
    }
}
