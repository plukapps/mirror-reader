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

@Test("PLN-006: en anual, prueba de 14 días; en mensual, elegir con el precio por mes")
func cta() {
    #expect(PlanCatalog.cta(.reader, .annual) == PlanCta(
        title: "Probar Lector 14 días gratis", subtitle: "Después $24.99 por año. Cancelás cuando quieras."
    ))
    #expect(PlanCatalog.cta(.reader, .monthly) == PlanCta(
        title: "Elegir Lector · $2.99 por mes", subtitle: "Se renueva cada mes. Cancelás cuando quieras."
    ))
    #expect(PlanCatalog.cta(.library, .annual).title == "Probar Biblioteca 14 días gratis")
    #expect(PlanCatalog.cta(.library, .monthly).title == "Elegir Biblioteca · $5.99 por mes")
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

@Test("PLN-001: la tabla comparativa usa el espacio en lugar de libros y no tiene tope por archivo")
func comparison() {
    let rows = PlanCatalog.comparison(quota: StorageQuota(usedBytes: 0, quotaBytes: 15 * mib))
    #expect(rows.first == PlanComparisonRow(feature: "Espacio en la nube", free: "15 MB", reader: "5 GB", library: "50 GB"))
    #expect(rows.count == 9)
    #expect(!rows.contains { $0.feature.contains("archivo") || $0.feature.contains("Libros") })
}
