import ReaderDomain
import Testing
@testable import Reader

/// Textos de los planes, traducibles (K-295). Antes estaban en `ReaderDomain`; ahora los arma la app, como Android.
@MainActor
struct PlanTextsTests {
    private let mib: Int64 = 1024 * 1024
    private let gib: Int64 = 1024 * 1024 * 1024

    @Test("PLN-001: nombre y bajada de cada plan")
    func names() {
        #expect(PlanId.allCases.map(\.name) == ["Free", "Plus", "Pro"])
        #expect(PlanId.plus.tagline == "Para quien lee siempre")
        #expect(PlanId.free.tagline == "Para leer de vez en cuando")
        #expect(PlanId.pro.tagline == "Para quien tiene una biblioteca")
    }

    @Test("PLN-004, PLN-005: período y equivalencia por mes según la forma de pago")
    func periodNotes() {
        #expect(PlanCatalog.plan(.plus).periodNote(.annual) == "Equivale a $2.08 por mes")
        #expect(PlanCatalog.plan(.plus).periodNote(.monthly) == "Pagás mes a mes")
        #expect(PlanCatalog.plan(.pro).periodNote(.annual) == "Equivale a $4.17 por mes")
        #expect(PlanCatalog.plan(.free).periodNote(.annual) == "Sin costo, para siempre")
        #expect(PlanCatalog.plan(.free).periodNote(.monthly) == "Sin costo, para siempre")
        #expect(Billing.annual.perLabel == "por año")
        #expect(Billing.monthly.perLabel == "por mes")
    }

    @Test("PLN-006: sin prueba gratis, siempre Elegir con el precio del período")
    func cta() {
        #expect(PlanCta(.plus, .annual) == PlanCta(
            title: "Elegir Plus · $24.99 por año", subtitle: "Se renueva cada año. Cancelás cuando quieras."
        ))
        #expect(PlanCta(.plus, .monthly) == PlanCta(
            title: "Elegir Plus · $2.99 por mes", subtitle: "Se renueva cada mes. Cancelás cuando quieras."
        ))
        #expect(PlanCta(.pro, .annual).title == "Elegir Pro · $49.99 por año")
        #expect(PlanCta(.pro, .monthly).title == "Elegir Pro · $5.99 por mes")
    }

    @Test("PLN-001, PLN-016: lo que incluye cada tarjeta, sin funciones en definición")
    func includes() {
        #expect(PlanId.free.includes == ["Lectura y notas sin tope", "Sincroniza en todos tus dispositivos", "Archivos de hasta 2 MB"])
        #expect(PlanId.plus.includes == ["Temas y fuentes", "Exportá notas a Markdown, Obsidian y Notion", "Archivos de hasta 50 MB"])
        #expect(PlanId.pro.includes == ["Todo lo de Plus", "Archivos de hasta 100 MB"])
    }

    @Test("PLN-003: \"Usás X de Y\"")
    func usage() {
        #expect(PlanUsage(StorageQuota(usedBytes: 12 * mib, quotaBytes: 15 * mib))?.text == "Usás 12 MB de 15 MB")
        #expect(PlanUsage(StorageQuota(usedBytes: 12 * mib, quotaBytes: 15 * mib))?.ratio == "80 %")
        #expect(PlanUsage(StorageQuota(usedBytes: 3 * gib / 2, quotaBytes: 5 * gib))?.text == "Usás 1,5 GB de 5 GB")
    }

    @Test("PLN-001, PLN-016: la tabla comparativa tiene las seis filas de la tabla de funciones")
    func comparison() {
        let rows = PlanComparisonRow.rows(quota: StorageQuota(usedBytes: 0, quotaBytes: 15 * mib))
        #expect(rows.map(\.feature) == [
            "Espacio en la nube", "Tamaño por archivo", "Lectura, ajustes y anotaciones",
            "Sincronización en todos tus dispositivos", "Temas y fuentes", "Exportar notas (Markdown, Obsidian, Notion)",
        ])
        #expect(rows[0] == PlanComparisonRow(feature: "Espacio en la nube", free: "15 MB", plus: "5 GB", pro: "50 GB"))
        #expect(rows[1] == PlanComparisonRow(feature: "Tamaño por archivo", free: "2 MB", plus: "50 MB", pro: "100 MB"))
        #expect(rows[4] == PlanComparisonRow(feature: "Temas y fuentes", free: "—", plus: "✓", pro: "✓"))
        let hidden = ["Estadísticas", "Drive", "Calibre", "web", "Familia"]
        let texts = rows.map(\.feature) + PlanId.allCases.flatMap(\.includes)
        #expect(!texts.contains { text in hidden.contains { text.contains($0) } })
    }
}
