import Foundation
import Observation
import ReaderDomain

/// Estado de la biblioteca (LIB-010, LIB-011), como `LibraryViewModel` de Android.
@MainActor
@Observable
final class LibraryViewModel {
    private(set) var loading = true
    /// Filtro activo. Inicio puede elegirlo antes de abrir la pantalla (HOM-011).
    private(set) var filter: LibraryFilter = .all
    private var allBooks: [LibraryBook] = []

    private let library: LibraryRepository
    private let sync: SyncCoordinator?
    private let quotaCache: QuotaCache?
    private let usageNoticePreference: UsageNoticePreference?
    /// PLN-011: el uso, si corresponde mostrar el aviso del 80 %; nil, sin aviso.
    private(set) var usageNotice: PlanUsage?
    /// GST-006: sin sesión no hay nada de la nube a la vista (estado de sincronización, aviso del 80 %).
    private(set) var signedIn = true
    private let isSignedIn: @Sendable () async -> Bool

    /// - Parameters:
    ///   - sync: nil si no se muestra el estado de la sincronización.
    ///   - quotaCache, usageNotice: la última cuota y si el aviso del 80 % ya se descartó (PLN-011); nil, sin aviso.
    ///   - isSignedIn: si hay sesión; se pregunta en cada lectura (al crear la cuenta, Inicio se arma de nuevo).
    init(
        library: LibraryRepository, sync: SyncCoordinator? = nil, quotaCache: QuotaCache? = nil,
        usageNotice: UsageNoticePreference? = nil, isSignedIn: @escaping @Sendable () async -> Bool = { true }
    ) {
        self.isSignedIn = isSignedIn
        self.library = library
        self.sync = sync
        self.quotaCache = quotaCache
        usageNoticePreference = usageNotice
    }

    /// PLN-011: cerrar el aviso o tocar "Ver planes" lo descarta para siempre en esta cuenta y este dispositivo.
    func dismissUsageNotice() {
        usageNoticePreference?.dismiss()
        usageNotice = nil
    }

    /// Libros importados en este dispositivo que aún no están en la nube (SYN-008).
    var pendingUploadCount: Int { allBooks.count { $0.isDownloaded && !$0.isUploaded } }

    /// Línea de estado de la sincronización (SYN-008), como `SyncStatus` de Android, o nil si no hay nada que decir.
    var syncStatus: SyncStatusLine? {
        guard signedIn, let state = sync?.state else { return nil }
        return SyncStatusLine(state: state, pendingUploads: pendingUploadCount)
    }

    /// Con un problema, tocar la línea de estado reintenta.
    func retrySync() {
        sync?.request()
    }

    /// Libros que pasan el filtro activo, con el importado más reciente primero.
    var books: [LibraryBook] { allBooks.filter(by: filter) }

    /// Cantidad de libros de cada pestaña, sin importar el filtro activo.
    func count(_ filter: LibraryFilter) -> Int { allBooks.count(by: filter) }

    /// No hay ningún libro, sin importar el filtro.
    var isLibraryEmpty: Bool { !loading && allBooks.isEmpty }

    func select(_ filter: LibraryFilter) {
        self.filter = filter
    }

    /// Lee la base local. Se llama al abrir la pantalla y cada vez que la sincronización la cambia (SYN-001).
    func load() async {
        allBooks = await library.books()
        signedIn = await isSignedIn()
        loading = false
        // La cuota guardada cambia con cada pasada de sincronización, que vuelve a llamar aquí.
        if !signedIn {
            usageNotice = nil
        } else if let quotaCache, let usageNoticePreference {
            let quota = quotaCache.last()
            usageNotice = shouldShowUsageNotice(quota, dismissed: usageNoticePreference.isDismissed()) ? PlanUsage(quota) : nil
        }
    }
}
