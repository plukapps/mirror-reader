import Testing
@testable import ReaderDomain

private let mib: Int64 = 1024 * 1024
private let gib: Int64 = 1024 * mib

@Test("PLN-001: tres planes; Gratis usa la cuota de la cuenta, Lector 5 GB y Biblioteca 50 GB")
func catalog() {
    #expect(PlanCatalog.all.map(\.id) == [.free, .reader, .library])
    #expect(PlanCatalog.storageBytes(.free, quota: StorageQuota(usedBytes: 0, quotaBytes: 20 * mib)) == 20 * mib)
    #expect(PlanCatalog.storageBytes(.free, quota: nil) == 15 * mib)
    #expect(PlanCatalog.storageBytes(.reader, quota: nil) == 5 * gib)
    #expect(PlanCatalog.storageBytes(.library, quota: nil) == 50 * gib)
    #expect(PlanCatalog.storageText(.free, quota: nil) == "15 MB")
    #expect(PlanCatalog.storageText(.reader, quota: nil) == "5 GB")
    #expect(PlanCatalog.storageText(.library, quota: nil) == "50 GB")
}

@Test("PLN-001, PLN-004: precio, período y equivalencia por mes según la forma de pago")
func prices() {
    let reader = PlanCatalog.plan(.reader)
    #expect(reader.price(.annual) == "$24.99")
    #expect(reader.price(.monthly) == "$2.99")
    #expect(reader.periodNote(.annual) == "Equivale a $2.08 por mes")
    #expect(reader.periodNote(.monthly) == "Pagás mes a mes")
    let library = PlanCatalog.plan(.library)
    #expect(library.price(.annual) == "$49.99")
    #expect(library.price(.monthly) == "$5.99")
    #expect(library.periodNote(.annual) == "Equivale a $4.17 por mes")
    #expect(PlanCatalog.plan(.free).price(.annual) == "$0")
    #expect(Billing.annual.perLabel == "por año")
    #expect(Billing.monthly.perLabel == "por mes")
}

@Test("PLN-006: sin prueba gratis, siempre Elegir con el precio del período")
func cta() {
    #expect(PlanCatalog.cta(.reader, .annual) == PlanCta(
        title: "Elegir Lector · $24.99 por año", subtitle: "Se renueva cada año. Cancelás cuando quieras."
    ))
    #expect(PlanCatalog.cta(.reader, .monthly) == PlanCta(
        title: "Elegir Lector · $2.99 por mes", subtitle: "Se renueva cada mes. Cancelás cuando quieras."
    ))
    #expect(PlanCatalog.cta(.library, .annual).title == "Elegir Biblioteca · $49.99 por año")
    #expect(PlanCatalog.cta(.library, .monthly).title == "Elegir Biblioteca · $5.99 por mes")
}

@Test("PLN-001, PLN-014: tamaño máximo por archivo de cada plan")
func maxFile() {
    #expect(PlanCatalog.plan(.free).maxFileBytes == 2 * mib)
    #expect(PlanCatalog.plan(.reader).maxFileBytes == 50 * mib)
    #expect(PlanCatalog.plan(.library).maxFileBytes == 100 * mib)
    #expect(PlanCatalog.maxFileText(.free) == "2 MB")
    #expect(PlanCatalog.maxFileText(.library) == "100 MB")
}

@Test("PLN-001, PLN-016: lo que incluye cada tarjeta, sin funciones en definición")
func includes() {
    #expect(PlanCatalog.free.includes == ["Lectura y notas sin tope", "Sincroniza en todos tus dispositivos", "Archivos de hasta 2 MB"])
    #expect(PlanCatalog.reader.includes == ["Temas y fuentes", "Exportá notas a Markdown, Obsidian y Notion", "Archivos de hasta 50 MB"])
    #expect(PlanCatalog.library.includes == ["Todo lo de Lector", "Archivos de hasta 100 MB"])
}

@Test("PLN-003: uso con la unidad del total, proporción y barra; sin cuota no hay fila")
func usage() {
    let line = PlanUsage(StorageQuota(usedBytes: 12 * mib, quotaBytes: 15 * mib))
    #expect(line?.text == "Usás 12 MB de 15 MB")
    #expect(line?.ratio == "80 %")
    #expect(line?.fraction == 0.8)
    #expect(line?.free == "3 MB")
    #expect(PlanUsage(StorageQuota(usedBytes: 3 * gib / 2, quotaBytes: 5 * gib))?.text == "Usás 1,5 GB de 5 GB")
    #expect(PlanUsage(StorageQuota(usedBytes: 20 * mib, quotaBytes: 15 * mib))?.fraction == 1)
    #expect(PlanUsage(nil) == nil)
}

@Test("PLN-001, PLN-016: la tabla comparativa tiene las seis filas de la tabla de funciones")
func comparison() {
    let rows = PlanCatalog.comparison(quota: StorageQuota(usedBytes: 0, quotaBytes: 15 * mib))
    #expect(rows.map(\.feature) == [
        "Espacio en la nube", "Tamaño por archivo", "Lectura, ajustes y anotaciones",
        "Sincronización en todos tus dispositivos", "Temas y fuentes", "Exportar notas (Markdown, Obsidian, Notion)",
    ])
    #expect(rows[0] == PlanComparisonRow(feature: "Espacio en la nube", free: "15 MB", reader: "5 GB", library: "50 GB"))
    #expect(rows[1] == PlanComparisonRow(feature: "Tamaño por archivo", free: "2 MB", reader: "50 MB", library: "100 MB"))
    #expect(rows[4] == PlanComparisonRow(feature: "Temas y fuentes", free: "—", reader: "✓", library: "✓"))
    let hidden = ["Estadísticas", "Drive", "Calibre", "web", "Familia"]
    let texts = rows.map(\.feature) + PlanCatalog.all.flatMap(\.includes)
    #expect(!texts.contains { text in hidden.contains { text.contains($0) } })
}

@Test("PLN-015: en Gratis el lector usa Clásico y serifa; tamaño e interlineado siguen; en Lector, lo guardado")
func effectiveSettings() {
    let saved = ReaderSettings(theme: .dark, fontScale: 1.3, font: .mono, lineSpacing: .wide)
    #expect(effectiveReaderSettings(saved, plan: .free) == ReaderSettings(theme: .light, fontScale: 1.3, font: .serif, lineSpacing: .wide))
    #expect(effectiveReaderSettings(saved, plan: .reader) == saved)
    #expect(effectiveReaderSettings(saved, plan: .library) == saved)
    #expect(ReadingTheme.allCases.filter(\.isPaid) == [.dark, .sepia])
    #expect(ReaderFont.allCases.filter(\.isPaid) == [.sans, .mono])
}
