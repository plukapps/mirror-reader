import Foundation
import ReaderDomain

// Textos de los planes (PLN-001 a PLN-006), traducibles con `String(localized:)` (K-295). El dominio solo tiene ids,
// números y reglas; como `strings.xml` en Android (`plans_*`).

extension PlanId {
    /// "Gratis", "Lector", "Biblioteca".
    var name: String {
        switch self {
        case .free: String(localized: "Gratis")
        case .plus: String(localized: "Lector")
        case .pro: String(localized: "Biblioteca")
        }
    }

    var tagline: String {
        switch self {
        case .free: String(localized: "Para leer de vez en cuando")
        case .plus: String(localized: "Para quien lee siempre")
        case .pro: String(localized: "Para quien tiene una biblioteca")
        }
    }

    /// PLN-001, PLN-016: lo que incluye la tarjeta; el máximo por archivo sale del plan.
    var includes: [String] {
        let maxFile = String(localized: "Archivos de hasta \(PlanCatalog.maxFileText(self))")
        return switch self {
        case .free: [String(localized: "Lectura y notas sin tope"), String(localized: "Sincroniza en todos tus dispositivos"), maxFile]
        case .plus: [String(localized: "Temas y fuentes"), String(localized: "Exportá notas a Markdown, Obsidian y Notion"), maxFile]
        case .pro: [String(localized: "Todo lo de Lector"), maxFile]
        }
    }
}

extension Billing {
    /// "por año" / "por mes", junto al precio de cada tarjeta.
    var perLabel: String {
        switch self {
        case .annual: String(localized: "por año")
        case .monthly: String(localized: "por mes")
        }
    }
}

extension Plan {
    /// PLN-004, PLN-005: "Equivale a $2.08 por mes" en anual, "Pagás mes a mes" en mensual; en Gratis, "Sin costo, para
    /// siempre".
    func periodNote(_ billing: Billing) -> String {
        guard let annualPerMonth else { return String(localized: "Sin costo, para siempre") }
        return switch billing {
        case .annual: String(localized: "Equivale a \(annualPerMonth) por mes")
        case .monthly: String(localized: "Pagás mes a mes")
        }
    }
}

extension PlanUsage {
    /// PLN-003: "Usás 12 MB de 15 MB".
    var text: String { String(localized: "Usás \(used) de \(total)") }
    /// "80 %", en letra monoespaciada a la derecha.
    var ratio: String { String(localized: "\(percent) %") }
}

/// Botón de compra de P01 y su texto de abajo (PLN-006). Sin prueba gratis por ahora: se elige con el precio del período.
struct PlanCta: Equatable {
    let title: String
    let subtitle: String

    init(title: String, subtitle: String) {
        self.title = title
        self.subtitle = subtitle
    }

    init(_ id: PlanId, _ billing: Billing) {
        let price = PlanCatalog.plan(id).price(billing)
        switch billing {
        case .annual:
            title = String(localized: "Elegir \(id.name) · \(price) por año")
            subtitle = String(localized: "Se renueva cada año. Cancelás cuando quieras.")
        case .monthly:
            title = String(localized: "Elegir \(id.name) · \(price) por mes")
            subtitle = String(localized: "Se renueva cada mes. Cancelás cuando quieras.")
        }
    }
}

/// Fila de la tabla comparativa (PLN-006).
struct PlanComparisonRow: Equatable {
    let feature: String
    let free: String
    let plus: String
    let pro: String

    /// PLN-001, PLN-006: las filas de la tabla comparativa. Sin funciones en definición (PLN-016).
    static func rows(quota: StorageQuota?) -> [PlanComparisonRow] {
        let yes = "✓", no = "—", unlimited = String(localized: "Sin tope")
        func storage(_ id: PlanId) -> String { PlanCatalog.storageText(id, quota: quota) }
        return [
            PlanComparisonRow(
                feature: String(localized: "Espacio en la nube"), free: storage(.free), plus: storage(.plus), pro: storage(.pro)
            ),
            PlanComparisonRow(
                feature: String(localized: "Tamaño por archivo"),
                free: PlanCatalog.maxFileText(.free), plus: PlanCatalog.maxFileText(.plus), pro: PlanCatalog.maxFileText(.pro)
            ),
            PlanComparisonRow(
                feature: String(localized: "Lectura, ajustes y anotaciones"), free: unlimited, plus: unlimited, pro: unlimited
            ),
            PlanComparisonRow(feature: String(localized: "Sincronización en todos tus dispositivos"), free: yes, plus: yes, pro: yes),
            PlanComparisonRow(feature: String(localized: "Temas y fuentes"), free: no, plus: yes, pro: yes),
            PlanComparisonRow(feature: String(localized: "Exportar notas (Markdown, Obsidian, Notion)"), free: no, plus: yes, pro: yes),
        ]
    }
}
