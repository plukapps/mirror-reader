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

private func check(used: Int64, _ items: [PendingUpload], quota: Int64 = 15 * mib) -> ImportQuotaCheck {
    ImportQuotaCheck(uploads: Pending(items: items), cache: Cache(StorageQuota(usedBytes: used, quotaBytes: quota)))
}

@Test("PLN-007, PLN-008: lleno, no entra o entra justo")
func blockRule() {
    #expect(quotaBlock(freeBytes: 0, bookBytes: mib) == .full)
    #expect(quotaBlock(freeBytes: 3 * mib, bookBytes: 8 * mib) == .doesNotFit(bookBytes: 8 * mib, freeBytes: 3 * mib))
    #expect(quotaBlock(freeBytes: 3 * mib, bookBytes: 3 * mib) == nil)
}

@Test("PLN-008: quedan 3 MB y el libro pesa 8 MB")
func doesNotFit() async {
    let notice = await check(used: 12 * mib, [item("a", 8 * mib)])(imported: ["a"])
    #expect(notice?.block == .doesNotFit(bookBytes: 8 * mib, freeBytes: 3 * mib))
    #expect(notice?.title == "Libro a")
}

@Test("PLN-007: con el espacio lleno, cualquier libro")
func full() async {
    #expect(await check(used: 15 * mib, [item("a", mib)])(imported: ["a"])?.block == .full)
}

@Test("PLN-009: se descuentan los pendientes que esperan subirse")
func pendingCount() async {
    let notice = await check(used: 5 * mib, [item("viejo", 8 * mib), item("a", 4 * mib)])(imported: ["a"])
    #expect(notice?.block == .doesNotFit(bookBytes: 4 * mib, freeBytes: 2 * mib))
}

@Test("PLN-009: varios a la vez, una sola hoja con el primero que no entra")
func firstThatDoesNotFit() async {
    let notice = await check(used: 0, [item("a", 10 * mib), item("b", 4 * mib), item("c", 9 * mib)])(imported: ["a", "b", "c"])
    #expect(notice?.bookId == "c")
    #expect(notice?.block == .doesNotFit(bookBytes: 9 * mib, freeBytes: mib))
}

@Test("PLN-009: si entra, o si nunca se leyó la cuota, no hay hoja")
func noNotice() async {
    #expect(await check(used: 0, [item("a", mib)])(imported: ["a"]) == nil)
    let unknown = ImportQuotaCheck(uploads: Pending(items: [item("a", 99 * mib)]), cache: Cache(nil))
    #expect(await unknown(imported: ["a"]) == nil)
    #expect(await check(used: 15 * mib, [item("a", mib)])(imported: []) == nil)
}
