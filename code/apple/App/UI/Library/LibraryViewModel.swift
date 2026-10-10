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

    /// - Parameter sync: nil si no se muestra el estado de la sincronización.
    init(library: LibraryRepository, sync: SyncCoordinator? = nil) {
        self.library = library
        self.sync = sync
    }

    /// Libros importados en este dispositivo que aún no están en la nube (SYN-008).
    var pendingUploadCount: Int { allBooks.count { $0.isDownloaded && !$0.isUploaded } }

    /// Línea de estado de la sincronización (SYN-008), como `SyncStatus` de Android, o nil si no hay nada que decir.
    var syncStatus: SyncStatusLine? {
        guard let state = sync?.state else { return nil }
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
        loading = false
    }
}
