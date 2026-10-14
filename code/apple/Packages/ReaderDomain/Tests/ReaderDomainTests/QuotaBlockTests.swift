import Foundation
import Testing
@testable import ReaderDomain

private let mib: Int64 = 1024 * 1024

private struct Pending: BookUploadRepository {
    let items: [PendingUpload]
    func pending() async throws -> [PendingUpload] { items }
    func markUploaded(bookId: String, sizeBytes: Int64, coverUploaded: Bool) async throws {}
}

private final class Cache: QuotaCache, @unchecked Sendable {
    let saved: StorageQuota?
    init(_ saved: StorageQuota?) { self.saved = saved }
    func last() -> StorageQuota? { saved }
    func save(_ quota: StorageQuota) {}
}

private func item(_ id: String, _ size: Int64) -> PendingUpload {
    PendingUpload(book: RemoteBook(id: id, title: "Libro \(id)", authors: [], sizeBytes: size), file: URL(filePath: "/\(id).epub"))
}

private func check(used: Int64, _ items: [PendingUpload], quota: Int64 = 15 * mib, plan: String = "free") -> ImportQuotaCheck {
    ImportQuotaCheck(uploads: Pending(items: items), cache: Cache(StorageQuota(usedBytes: used, quotaBytes: quota, plan: plan)))
}

@Test("PLN-007, PLN-008: lleno, no entra o entra justo")
func blockRule() {
    #expect(quotaBlock(freeBytes: 0, bookBytes: mib) == .full)
    #expect(quotaBlock(freeBytes: 3 * mib, bookBytes: 8 * mib) == .doesNotFit(bookBytes: 8 * mib, freeBytes: 3 * mib))
    #expect(quotaBlock(freeBytes: 3 * mib, bookBytes: 3 * mib) == nil)
}

@Test("PLN-008: quedan 3 MB y el libro pesa 8 MB")
func doesNotFit() async {
    let notice = await check(used: 12 * mib, [item("a", 8 * mib)], plan: "pro")(imported: ["a"])
    #expect(notice?.block == .doesNotFit(bookBytes: 8 * mib, freeBytes: 3 * mib))
    #expect(notice?.title == "Libro a")
}

@Test("PLN-007: con el espacio lleno, cualquier libro")
func full() async {
    #expect(await check(used: 15 * mib, [item("a", mib)])(imported: ["a"])?.block == .full)
}

@Test("PLN-009: se descuentan los pendientes que esperan subirse")
func pendingCount() async {
    let notice = await check(used: 5 * mib, [item("viejo", 8 * mib), item("a", 4 * mib)], plan: "pro")(imported: ["a"])
    #expect(notice?.block == .doesNotFit(bookBytes: 4 * mib, freeBytes: 2 * mib))
}

@Test("PLN-009: varios a la vez, una sola hoja con el primero que no entra")
func firstThatDoesNotFit() async {
    let notice = await check(used: 0, [item("a", 10 * mib), item("b", 4 * mib), item("c", 9 * mib)], plan: "pro")(imported: ["a", "b", "c"])
    #expect(notice?.bookId == "c")
    #expect(notice?.block == .doesNotFit(bookBytes: 9 * mib, freeBytes: mib))
}

@Test("PLN-009: si entra, o si nunca se leyó la cuota y no supera 2 MB, no hay hoja")
func noNotice() async {
    #expect(await check(used: 0, [item("a", mib)])(imported: ["a"]) == nil)
    let unknown = ImportQuotaCheck(uploads: Pending(items: [item("a", 2 * mib)]), cache: Cache(nil))
    #expect(await unknown(imported: ["a"]) == nil)
    #expect(await check(used: 15 * mib, [item("a", mib)])(imported: []) == nil)
}

@Test("PLN-011: el aviso va del 80 % al lleno, sin incluirlo, y no vuelve si se descartó")
func usageNoticeRule() {
    let at = { (used: Int64) in StorageQuota(usedBytes: used * mib, quotaBytes: 15 * mib) }
    #expect(shouldShowUsageNotice(at(12), dismissed: false))
    #expect(shouldShowUsageNotice(at(14), dismissed: false))
    #expect(!shouldShowUsageNotice(StorageQuota(usedBytes: 12 * mib - 1, quotaBytes: 15 * mib), dismissed: false))
    #expect(!shouldShowUsageNotice(at(15), dismissed: false))
    #expect(!shouldShowUsageNotice(at(13), dismissed: true))
    #expect(!shouldShowUsageNotice(nil, dismissed: false))
    #expect(!shouldShowUsageNotice(StorageQuota(usedBytes: 0, quotaBytes: 0), dismissed: false))
}

@Test("PLN-014: el tamaño máximo se comprueba antes que el espacio")
func tooLargeFirst() {
    #expect(quotaBlock(freeBytes: 0, bookBytes: 3 * mib, maxFileBytes: 2 * mib) == .tooLarge(bookBytes: 3 * mib, maxBytes: 2 * mib))
    #expect(quotaBlock(freeBytes: 10 * mib, bookBytes: 2 * mib, maxFileBytes: 2 * mib) == nil)
}

@Test("PLN-014: en Free un libro de 3 MB es muy grande; en Plus entra")
func tooLargeByPlan() async {
    #expect(await check(used: 0, [item("a", 3 * mib)])(imported: ["a"])?.block == .tooLarge(bookBytes: 3 * mib, maxBytes: 2 * mib))
    #expect(await check(used: 0, [item("a", 3 * mib)], plan: "plus")(imported: ["a"]) == nil)
    #expect(await check(used: 0, [item("a", 51 * mib)], quota: 5 * 1024 * mib, plan: "plus")(imported: ["a"])?.block
        == .tooLarge(bookBytes: 51 * mib, maxBytes: 50 * mib))
}

@Test("PLN-014: un plan desconocido vale como Free; justo 2 MB entra")
func unknownPlanIsFree() async {
    #expect(PlanCatalog.planOf(StorageQuota(usedBytes: 0, quotaBytes: 1, plan: "premium")) == .free)
    #expect(PlanCatalog.planOf(nil) == .free)
    #expect(PlanCatalog.planOf(StorageQuota(usedBytes: 0, quotaBytes: 1, plan: "pro")) == .pro)
    #expect(PlanCatalog.planOf(StorageQuota(usedBytes: 0, quotaBytes: 1, plan: "plus")) == .plus)
    // ACC-007: los ids del backend son `plus` y `pro`; los viejos ya no valen.
    #expect(PlanCatalog.planOf(StorageQuota(usedBytes: 0, quotaBytes: 1, plan: "library")) == .free)
    #expect(PlanCatalog.planOf(StorageQuota(usedBytes: 0, quotaBytes: 1, plan: "reader")) == .free)
    #expect(await check(used: 0, [item("a", 3 * mib)], plan: "premium")(imported: ["a"])?.block == .tooLarge(bookBytes: 3 * mib, maxBytes: 2 * mib))
    #expect(await check(used: 0, [item("a", 2 * mib)])(imported: ["a"]) == nil)
}

@Test("ACC-008, ACC-010: el máximo por archivo que informa el servidor manda sobre el fijo del plan")
func serverMaxFileBytesWins() async {
    #expect(PlanCatalog.maxFileBytes(nil) == 2 * mib)
    #expect(PlanCatalog.maxFileBytes(StorageQuota(usedBytes: 0, quotaBytes: 1, plan: "plus")) == 50 * mib)
    #expect(PlanCatalog.maxFileBytes(StorageQuota(usedBytes: 0, quotaBytes: 1, plan: "plus", maxFileBytes: 7 * mib)) == 7 * mib)
    let cache = Cache(StorageQuota(usedBytes: 0, quotaBytes: 15 * mib, maxFileBytes: 1 * mib))
    let notice = await ImportQuotaCheck(uploads: Pending(items: [item("a", 1 * mib + 1)]), cache: cache)(imported: ["a"])
    #expect(notice?.block == .tooLarge(bookBytes: 1 * mib + 1, maxBytes: 1 * mib))
}

@Test("PLN-014: un pendiente que supera el máximo no ocupa lugar")
func oversizePendingTakesNoSpace() async {
    let notice = await check(used: 10 * mib, [item("grande", 4 * mib), item("a", 2 * mib)])(imported: ["a"])
    #expect(notice == nil)
}

@Test("PLN-014: sin cuota leída, el tamaño se comprueba con el máximo de Free")
func tooLargeWithoutQuota() async {
    let check = ImportQuotaCheck(uploads: Pending(items: [item("a", 3 * mib)]), cache: Cache(nil))
    let notice = await check(imported: ["a"])
    #expect(notice?.block == .tooLarge(bookBytes: 3 * mib, maxBytes: 2 * mib))
    #expect(notice?.quota == nil)
}

@Test("PLN-014: se ofrece Plus desde Free, Pro desde Plus y nada desde Pro")
func nextPlan() {
    #expect(PlanCatalog.nextPlan(after: .free) == .plus)
    #expect(PlanCatalog.nextPlan(after: .plus) == .pro)
    #expect(PlanCatalog.nextPlan(after: .pro) == nil)
}
