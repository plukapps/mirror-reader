import Foundation
import ReaderDomain
import OSLog
import SwiftData

/// Arma las dependencias de la app (ADR 0010: sin framework de inyección, por inicializador).
@MainActor
enum AppGraph {
    /// ViewModels de las pantallas principales, sobre la misma base local, y cómo abrir el lector.
    struct Screens {
        let home: HomeViewModel
        let search: SearchViewModel
        let library: LibraryViewModel
        let sync: SyncCoordinator
        /// Menú de un libro con mantener presionado (LIB-016).
        let bookActions: BookActionsViewModel
        /// nil en la Mac: Readium Swift solo soporta iOS (ADR 0013).
        let makeReader: ((_ bookId: String) -> ReaderViewModel)?
    }

    static func makeScreens() -> Screens {
        let files = LibraryFiles.standard
        let store = LibraryStore(container: container(), files: files)
        // Los tests corren dentro de la app: nunca tocan el proyecto real.
        let firebase = !isRunningTests && FirebaseSetup.configureIfAvailable()
        let positions = firebase ? positionSync(store: store) : nil
        // LIB-020: quitar un libro lo borra de la cuenta y de los otros dispositivos.
        let removals = firebase ? RemovalSync(remote: FirebaseRemovals(), local: store, account: FirebaseAccountRepository()) : nil
        #if os(iOS)
        // LIB-012: un libro bajado de la nube saca la portada del EPUB si todavía no la tiene, como Android.
        let loader = PublicationLoader()
        let importer = EpubImporter(store: store, files: files, loader: loader)
        let bookFiles: BookFiles = CoverExtractingBookFiles(store: store, importer: importer)
        let reader: (importer: BookImporter, makeReader: (String) -> ReaderViewModel)? = readerParts(
            store: store, files: files, loader: loader, importer: importer, bookFiles: bookFiles, firebase: firebase, positions: positions
        )
        #else
        let bookFiles: BookFiles = store
        let reader: (importer: BookImporter, makeReader: (String) -> ReaderViewModel)? = nil
        #endif

        // La sincronización vuelve a leer la base en Inicio y en la biblioteca cada vez que la cambia.
        let refs = ScreenRefs()
        let pass = positions.map { libraryPass(store: store, bookFiles: bookFiles, positions: $0, removals: removals) }
        // SYN-015: un libro agregado en otro dispositivo pide una pasada (la app a la vista).
        let watcher = firebase
            ? LibraryWatcher(remote: FirestoreRemoteLibrary(), known: store, account: FirebaseAccountRepository()) {
                await MainActor.run { refs.sync?.request() }
            }
            : nil
        let sync = SyncCoordinator(pass: pass, positions: positions, removals: removals, watcher: watcher) {
            await refs.home?.reload()
            await refs.library?.load()
        }
        let library = LibraryViewModel(library: store, sync: sync)
        let home = HomeViewModel(library: store, importer: reader?.importer) { sync.request() }
        refs.home = home
        refs.library = library
        refs.sync = sync
        let search = SearchViewModel(library: store)
        let bookActions = BookActionsViewModel(
            // La posición sale en cuanto se pueda, sin esperar la red (SYN-011).
            actions: BookActions(
                positions: store,
                editor: store,
                requestFlush: { if let positions { Task { await positions.flush() } } },
                requestRemovalPush: { if let removals { Task { await removals.push() } } }
            ),
            reload: {
                await home.reload()
                await library.load()
                await search.load()
            },
            notify: { [weak home] in home?.show($0) }
        )
        return Screens(
            home: home,
            search: search,
            library: library,
            sync: sync,
            bookActions: bookActions,
            makeReader: reader?.makeReader
        )
    }

    /// Una pasada de sincronización de la biblioteca con Firebase (SYN-001), como `SyncLibraryUseCase` de Android.
    private static func libraryPass(
        store: LibraryStore, bookFiles: BookFiles, positions: PositionSync, removals: RemovalSync?
    ) -> SyncCoordinator.Pass {
        let remote = FirestoreRemoteLibrary()
        let books = FirebaseBookStore()
        let covers = FirebaseCoverStore()
        let sync = LibrarySync(
            account: FirebaseAccountRepository(),
            credentials: FirebaseSetup.devCredentials(),
            remote: remote,
            covers: covers,
            cloud: store,
            upload: UploadBooks(uploads: store, files: books, covers: covers, library: remote, quota: FirestoreQuotaSource()),
            download: DownloadBook(files: bookFiles, store: books),
            positions: positions,
            removals: removals
        )
        let log = Logger(subsystem: "com.pluk.reader", category: "LibrarySync")
        return { onChange in
            let outcome = await sync.run(onChange: onChange)
            log.info("Sincronización: \(String(describing: outcome), privacy: .public)")
            return outcome
        }
    }

    /// La posición de lectura entre dispositivos (SYN-011 a SYN-013, ADR 0011), como `PositionSync` de Android.
    private static func positionSync(store: LibraryStore) -> PositionSync {
        PositionSync(
            remote: FirestorePositions(),
            store: store,
            prefs: UserDefaultsSyncPreferences(deviceName: UserDefaultsSyncPreferences.model),
            account: FirebaseAccountRepository()
        )
    }

    /// Importador y lector, solo donde hay motor de EPUB (iOS, ADR 0013).
    #if os(iOS)
    private static func readerParts(
        store: LibraryStore, files: LibraryFiles, loader: PublicationLoader, importer: EpubImporter, bookFiles: BookFiles,
        firebase: Bool, positions: PositionSync?
    ) -> (importer: BookImporter, makeReader: (String) -> ReaderViewModel) {
        let bookStore: BookFileStore = firebase ? FirebaseBookStore() : OfflineBookStore()
        let source = EpubBookSource(store: store, files: files, loader: loader, download: DownloadBook(files: bookFiles, store: bookStore))
        let settings = UserDefaultsReaderSettings()
        let readerSync = positions.map { positions in
            CloudReaderSync(
                resolver: ResolveOpeningPosition(
                    remote: FirestorePositions(), store: store, positions: store, account: FirebaseAccountRepository()
                ),
                positions: positions
            )
        }
        return (
            importer,
            { bookId in ReaderViewModel(bookId: bookId, source: source, positions: store, settings: settings, sync: readerSync) }
        )
    }
    #endif

    private static var isRunningTests: Bool {
        ProcessInfo.processInfo.environment["XCTestConfigurationFilePath"] != nil
    }

    /// Base en disco; si no se puede abrir, una en memoria para que la app arranque igual.
    private static func container() -> ModelContainer {
        do {
            return try LibraryStore.persistentContainer()
        } catch {
            assertionFailure("No se pudo abrir la base local: \(error)")
            return try! LibraryStore.inMemoryContainer()
        }
    }
}

/// Pantallas que la sincronización recarga. Se completan después de armar el coordinador, que las necesita.
@MainActor
private final class ScreenRefs {
    weak var home: HomeViewModel?
    weak var library: LibraryViewModel?
    weak var sync: SyncCoordinator?
}

/// Sin Firebase no hay de dónde bajar libros: un libro solo en la nube no se puede abrir (ADR 0002).
private struct OfflineBookStore: BookFileStore {
    func downloadBook(bookId: String, to destination: URL) async throws {
        throw RemoteUnavailableError("Sin configuración de Firebase.")
    }

    func uploadBook(bookId: String, from file: URL) async throws {
        throw RemoteUnavailableError("Sin configuración de Firebase.")
    }
}
