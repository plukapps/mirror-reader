import Testing
@testable import ReaderDomain

private let mib: Int64 = 1024 * 1024
private let gib: Int64 = 1024 * mib

@Test("PLN-001: tres planes; Free usa la cuota de la cuenta, Plus 5 GB y Pro 50 GB")
func catalog() {
    #expect(PlanCatalog.all.map(\.id) == [.free, .plus, .pro])
    #expect(PlanCatalog.storageBytes(.free, quota: StorageQuota(usedBytes: 0, quotaBytes: 20 * mib)) == 20 * mib)
    #expect(PlanCatalog.storageBytes(.free, quota: nil) == 15 * mib)
    #expect(PlanCatalog.storageBytes(.plus, quota: nil) == 5 * gib)
    #expect(PlanCatalog.storageBytes(.pro, quota: nil) == 50 * gib)
    #expect(PlanCatalog.storageText(.free, quota: nil) == "15 MB")
    #expect(PlanCatalog.storageText(.plus, quota: nil) == "5 GB")
    #expect(PlanCatalog.storageText(.pro, quota: nil) == "50 GB")
}

@Test("PLN-001 (K-291): en una cuenta paga, Free muestra la cuota de Free y no la de la cuenta")
func freeStorageOfAPaidAccount() {
    #expect(PlanCatalog.storageBytes(.free, quota: StorageQuota(usedBytes: 0, quotaBytes: 50 * gib, plan: "pro")) == 15 * mib)
    #expect(PlanCatalog.storageText(.free, quota: StorageQuota(usedBytes: 0, quotaBytes: 5 * gib, plan: "plus")) == "15 MB")
}

@Test("PLN-001, PLN-004: precio según la forma de pago")
func prices() {
    let plus = PlanCatalog.plan(.plus)
    #expect(plus.price(.annual) == "$24.99")
    #expect(plus.price(.monthly) == "$2.99")
    #expect(plus.annualPerMonth == "$2.08")
    let pro = PlanCatalog.plan(.pro)
    #expect(pro.price(.annual) == "$49.99")
    #expect(pro.price(.monthly) == "$5.99")
    #expect(PlanCatalog.plan(.free).price(.annual) == "$0")
    #expect(PlanCatalog.plan(.free).annualPerMonth == nil)
}

@Test("PLN-001, PLN-014: tamaño máximo por archivo de cada plan")
func maxFile() {
    #expect(PlanCatalog.plan(.free).maxFileBytes == 2 * mib)
    #expect(PlanCatalog.plan(.plus).maxFileBytes == 50 * mib)
    #expect(PlanCatalog.plan(.pro).maxFileBytes == 100 * mib)
    #expect(PlanCatalog.maxFileText(.free) == "2 MB")
    #expect(PlanCatalog.maxFileText(.pro) == "100 MB")
}

@Test("PLN-003: uso con la unidad del total, proporción y barra; sin cuota no hay fila")
func usage() {
    let line = PlanUsage(StorageQuota(usedBytes: 12 * mib, quotaBytes: 15 * mib))
    #expect(line?.used == "12 MB")
    #expect(line?.total == "15 MB")
    #expect(line?.percent == 80)
    #expect(line?.fraction == 0.8)
    #expect(line?.free == "3 MB")
    #expect(PlanUsage(StorageQuota(usedBytes: 3 * gib / 2, quotaBytes: 5 * gib))?.used == "1,5 GB")
    #expect(PlanUsage(StorageQuota(usedBytes: 20 * mib, quotaBytes: 15 * mib))?.fraction == 1)
    #expect(PlanUsage(nil) == nil)
}

@Test("PLN-015: en Free el lector usa Clásico y serifa; tamaño e interlineado siguen; en Plus, lo guardado")
func effectiveSettings() {
    let saved = ReaderSettings(theme: .dark, fontScale: 1.3, font: .mono, lineSpacing: .wide)
    #expect(effectiveReaderSettings(saved, plan: .free) == ReaderSettings(theme: .light, fontScale: 1.3, font: .serif, lineSpacing: .wide))
    #expect(effectiveReaderSettings(saved, plan: .plus) == saved)
    #expect(effectiveReaderSettings(saved, plan: .pro) == saved)
    #expect(ReadingTheme.allCases.filter(\.isPaid) == [.dark, .sepia])
    #expect(ReaderFont.allCases.filter(\.isPaid) == [.sans, .mono])
}
