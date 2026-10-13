import Testing
@testable import ReaderDomain

private let mib: Int64 = 1024 * 1024
private let gib: Int64 = 1024 * mib

struct ProfileRulesTests {
    @Test("PRF-006: un total de 1 GB o más se muestra en GB; uno menor, en MB")
    func unitFollowsTheTotal() {
        #expect(StorageUnit.forTotal(2 * gib) == .gb)
        #expect(StorageUnit.forTotal(gib) == .gb)
        #expect(StorageUnit.forTotal(15 * mib) == .mb)
    }

    @Test("PRF-006: un decimal como máximo y coma decimal, como Android")
    func formatsWithOneDecimal() {
        #expect(formatStorage(gib * 12 / 10, unit: .gb) == "1,2 GB")
        #expect(formatStorage(gib * 8 / 10, unit: .gb) == "0,8 GB")
        #expect(formatStorage(2 * gib, unit: .gb) == "2 GB")
        #expect(formatStorage(15 * mib, unit: .mb) == "15 MB")
        #expect(formatStorage(mib * 34 / 10, unit: .mb) == "3,4 MB")
        #expect(formatStorage(200 * 1024, unit: .mb) == "0,2 MB")
    }

    @Test("PRF-006: cero y negativos se muestran como cero")
    func zeroAndNegative() {
        #expect(formatStorage(0, unit: .gb) == "0 GB")
        #expect(formatStorage(-5, unit: .mb) == "0 MB")
    }

    @Test("PRF-006: el plan gratuito es el que escribe el backend")
    func freePlan() {
        #expect(StorageQuota(usedBytes: 0, quotaBytes: 1).isFreePlan)
        #expect(!StorageQuota(usedBytes: 0, quotaBytes: 1, plan: "plus").isFreePlan)
    }

    @Test("PRF-013: el tamaño de letra en puntos, sobre la base de 18")
    func fontPoints() {
        #expect(readerFontPoints(fontTenths: 10) == 18)
        #expect(readerFontPoints(fontTenths: 11) == 20)
        #expect(readerFontPoints(fontTenths: 5) == 9)
    }
}
