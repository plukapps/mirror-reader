import Foundation

/// Por qué un libro importado no se va a subir (PLN-007, PLN-008).
public enum QuotaBlock: Equatable, Sendable {
    /// P02a: el espacio ya está lleno.
    case full
    /// P02b: queda espacio, pero este libro no entra.
    case doesNotFit(bookBytes: Int64, freeBytes: Int64)
    /// PLN-014: el archivo supera el tamaño máximo del plan.
    case tooLarge(bookBytes: Int64, maxBytes: Int64)
}

/// PLN-014, PLN-007, PLN-008: ¿el archivo entra en el plan y en el espacio libre que queda (ya descontados los otros
/// pendientes)? El tamaño máximo se comprueba primero.
public func quotaBlock(freeBytes: Int64, bookBytes: Int64, maxFileBytes: Int64 = .max) -> QuotaBlock? {
    if bookBytes > maxFileBytes { return .tooLarge(bookBytes: bookBytes, maxBytes: maxFileBytes) }
    if freeBytes <= 0 { return .full }
    if bookBytes > freeBytes { return .doesNotFit(bookBytes: bookBytes, freeBytes: freeBytes) }
    return nil
}

/// La hoja de P02 lista para mostrar: el libro que no entra y la cuota de la cuenta.
public struct QuotaBlockNotice: Equatable, Sendable, Identifiable {
    public let block: QuotaBlock
    public let bookId: String
    public let title: String
    /// nil si nunca se leyó: entonces solo puede ser `tooLarge`, con el máximo de Gratis (PLN-014).
    public let quota: StorageQuota?

    public init(block: QuotaBlock, bookId: String, title: String, quota: StorageQuota?) {
        self.block = block
        self.bookId = bookId
        self.title = title
        self.quota = quota
    }

    public var id: String { bookId }
}

/// PLN-009, PLN-014: después de importar, ¿alguno de los libros nuevos no entra en la nube? Usa la última cuota
/// conocida (sin red) y descuenta los pendientes de subir en el mismo orden en que se suben (`UploadBooks`): un
/// pendiente que no entra no ocupa lugar. Sin cuota conocida solo se comprueba el tamaño máximo de Gratis (vale LIB-009
/// para el espacio). Devuelve el primero que no entra.
public struct ImportQuotaCheck: Sendable {
    private let uploads: BookUploadRepository
    private let cache: QuotaCache

    public init(uploads: BookUploadRepository, cache: QuotaCache) {
        self.uploads = uploads
        self.cache = cache
    }

    public func callAsFunction(imported: [String]) async -> QuotaBlockNotice? {
        guard !imported.isEmpty, let pending = try? await uploads.pending() else { return nil }
        let quota = cache.last()
        let newBooks = Set(imported)
        let maxFile = PlanCatalog.maxFileBytes(quota)
        // Sin cuota, el espacio no se comprueba: solo el tamaño.
        var free = quota?.freeBytes ?? .max
        for item in pending {
            let size = item.book.sizeBytes
            if newBooks.contains(item.book.id),
               let block = quotaBlock(freeBytes: free, bookBytes: size, maxFileBytes: maxFile) {
                return QuotaBlockNotice(block: block, bookId: item.book.id, title: item.book.title, quota: quota)
            }
            // Como en `UploadBooks`: lo que supera el máximo o no entra no se sube y no ocupa lugar.
            if size <= maxFile, size <= free { free -= size }
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
