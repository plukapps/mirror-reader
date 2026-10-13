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
    /// PLN-014: tamaño máximo de cada archivo que se sube a la nube.
    public let maxFileBytes: Int64
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
    public static let annualSavingPercent = 30

    public static let free = Plan(
        id: .free, name: "Gratis", tagline: "Para leer de vez en cuando", storageBytes: nil, maxFileBytes: 2 * mib,
        includes: ["Lectura y notas sin tope", "Sincroniza en todos tus dispositivos", "Archivos de hasta 2 MB"],
        monthlyPrice: "$0", annualPrice: "$0", annualPerMonth: nil
    )
    public static let reader = Plan(
        id: .reader, name: "Lector", tagline: "Para quien lee siempre", storageBytes: 5 * gib, maxFileBytes: 50 * mib,
        includes: ["Temas y fuentes", "Exportá notas a Markdown, Obsidian y Notion", "Archivos de hasta 50 MB"],
        monthlyPrice: "$2.99", annualPrice: "$24.99", annualPerMonth: "$2.08"
    )
    public static let library = Plan(
        id: .library, name: "Biblioteca", tagline: "Para quien tiene una biblioteca", storageBytes: 50 * gib,
        maxFileBytes: 100 * mib,
        includes: ["Todo lo de Lector", "Archivos de hasta 100 MB"],
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

    /// PLN-014: el plan de la cuenta sale de la cuota; si no se conoce o no es uno de los tres, Gratis.
    public static func planOf(_ quota: StorageQuota?) -> PlanId {
        quota.flatMap { PlanId(rawValue: $0.plan) } ?? .free
    }

    /// PLN-014: el plan que se ofrece para pasar de este ("Con Lector…", "Con Biblioteca…"); nil en Biblioteca.
    public static func nextPlan(after id: PlanId) -> PlanId? {
        switch id {
        case .free: .reader
        case .reader: .library
        case .library: nil
        }
    }

    /// "2 MB", "50 MB": el tamaño máximo por archivo del plan (PLN-014).
    public static func maxFileText(_ id: PlanId) -> String {
        let bytes = plan(id).maxFileBytes
        return formatStorage(bytes, unit: StorageUnit.forTotal(bytes))
    }

    /// PLN-006: sin prueba gratis por ahora, se elige con el precio del período.
    public static func cta(_ id: PlanId, _ billing: Billing) -> PlanCta {
        let plan = plan(id)
        return PlanCta(
            title: "Elegir \(plan.name) · \(plan.price(billing)) por \(billing.period)",
            subtitle: "Se renueva cada \(billing.period). Cancelás cuando quieras."
        )
    }

    /// PLN-001, PLN-006: filas de la tabla comparativa. Sin funciones en definición (PLN-016).
    public static func comparison(quota: StorageQuota?) -> [PlanComparisonRow] {
        let yes = "✓", no = "—"
        return [
            PlanComparisonRow(
                feature: "Espacio en la nube", free: storageText(.free, quota: quota),
                reader: storageText(.reader, quota: quota), library: storageText(.library, quota: quota)
            ),
            PlanComparisonRow(
                feature: "Tamaño por archivo", free: maxFileText(.free), reader: maxFileText(.reader), library: maxFileText(.library)
            ),
            PlanComparisonRow(feature: "Lectura, ajustes y anotaciones", free: "Sin tope", reader: "Sin tope", library: "Sin tope"),
            PlanComparisonRow(feature: "Sincronización en todos tus dispositivos", free: yes, reader: yes, library: yes),
            PlanComparisonRow(feature: "Temas y fuentes", free: no, reader: yes, library: yes),
            PlanComparisonRow(feature: "Exportar notas (Markdown, Obsidian, Notion)", free: no, reader: yes, library: yes),
        ]
    }
}

// MARK: Temas y fuentes (PLN-015)

public extension PlanId {
    /// PLN-015: temas y fuentes son de Lector y Biblioteca.
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

/// PLN-015: lo que el lector aplica. En Gratis, Clásico y serifa; el resto de lo guardado (tamaño, interlineado) sigue
/// igual. La preferencia guardada no se toca: vuelve si la cuenta pasa a un plan pago.
public func effectiveReaderSettings(_ saved: ReaderSettings, plan: PlanId) -> ReaderSettings {
    guard !plan.hasThemesAndFonts else { return saved }
    return saved.withTheme(.light).withFont(.serif)
}
