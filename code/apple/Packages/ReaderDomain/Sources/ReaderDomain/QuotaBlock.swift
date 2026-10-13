import Foundation

/// Por qué un libro importado no se va a subir (PLN-007, PLN-008).
public enum QuotaBlock: Equatable, Sendable {
    /// P02a: el espacio ya está lleno.
    case full
    /// P02b: queda espacio, pero este libro no entra.
    case doesNotFit(bookBytes: Int64, freeBytes: Int64)
}

/// PLN-007, PLN-008: con el espacio libre que queda (ya descontados los otros pendientes), ¿entra este libro?
public func quotaBlock(freeBytes: Int64, bookBytes: Int64) -> QuotaBlock? {
    if freeBytes <= 0 { return .full }
    if bookBytes > freeBytes { return .doesNotFit(bookBytes: bookBytes, freeBytes: freeBytes) }
    return nil
}

/// La hoja de P02 lista para mostrar: el libro que no entra y la cuota de la cuenta.
public struct QuotaBlockNotice: Equatable, Sendable, Identifiable {
    public let block: QuotaBlock
    public let bookId: String
    public let title: String
    public let quota: StorageQuota

    public init(block: QuotaBlock, bookId: String, title: String, quota: StorageQuota) {
        self.block = block
        self.bookId = bookId
        self.title = title
        self.quota = quota
    }

    public var id: String { bookId }
}

/// PLN-009: después de importar, ¿alguno de los libros nuevos no entra en la nube? Usa la última cuota conocida
/// (sin red) y descuenta los pendientes de subir en el mismo orden en que se suben (`UploadBooks`): un pendiente que
/// no entra no ocupa lugar. Sin cuota conocida no hay hoja (vale LIB-009). Devuelve el primero que no entra.
public struct ImportQuotaCheck: Sendable {
    private let uploads: BookUploadRepository
    private let cache: QuotaCache

    public init(uploads: BookUploadRepository, cache: QuotaCache) {
        self.uploads = uploads
        self.cache = cache
    }

    public func callAsFunction(imported: [String]) async -> QuotaBlockNotice? {
        guard !imported.isEmpty, let quota = cache.last(), let pending = try? await uploads.pending() else { return nil }
        let newBooks = Set(imported)
        var free = quota.freeBytes
        for item in pending {
            let size = item.book.sizeBytes
            if newBooks.contains(item.book.id), let block = quotaBlock(freeBytes: free, bookBytes: size) {
                return QuotaBlockNotice(block: block, bookId: item.book.id, title: item.book.title, quota: quota)
            }
            if size <= free { free -= size }
        }
        return nil
    }
}

/// PLN-011: el aviso suave aparece con el uso en el 80 % o más de la cuota, sin llenarla, y si no se descartó.
public func shouldShowUsageNotice(_ quota: StorageQuota?, dismissed: Bool) -> Bool {
    guard !dismissed, let quota, quota.quotaBytes > 0 else { return false }
    return quota.usedBytes * 10 >= quota.quotaBytes * 8 && quota.usedBytes < quota.quotaBytes
}

/// PLN-011: el aviso del 80 % se descarta una vez por cuenta y dispositivo. La implementación vive en la app.
public protocol UsageNoticePreference: Sendable {
    func isDismissed() -> Bool
    func dismiss()
}
