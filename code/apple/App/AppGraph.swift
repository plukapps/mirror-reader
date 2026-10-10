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
        /// nil en la Mac: Readium Swift solo soporta iOS (ADR 0013).
        let makeReader: ((_ bookId: String) -> ReaderViewModel)?
    }

    static func makeScreens() -> Screens {
        let files = LibraryFiles.standard
        let store = LibraryStore(container: container(), files: files)
        // Los tests corren dentro de la app: nunca tocan el proyecto real.
        let firebase = !isRunningTests && FirebaseSetup.configureIfAvailable()
        let positions = firebase ? positionSync(store: store) : nil
        let reader = readerParts(store: store, files: files, firebase: firebase, positions: positions)

        // La sincronización vuelve a leer la base en Inicio y en la biblioteca cada vez que la cambia.
        let refs = ScreenRefs()
        let pass = positions.map { libraryPass(store: store, positions: $0) }
        let sync = SyncCoordinator(pass: pass, positions: positions) {
            await refs.home?.reload()
            await refs.library?.load()
        }
        let library = LibraryViewModel(library: store, sync: sync)
        let home = HomeViewModel(library: store, importer: reader?.importer) { sync.request() }
        refs.home = home
        refs.library = library
        return Screens(
            home: home,
            search: SearchViewModel(library: store),
            library: library,
            sync: sync,
            makeReader: reader?.makeReader
        )
    }

    /// Una pasada de sincronización de la biblioteca con Firebase (SYN-001), como `SyncLibraryUseCase` de Android.
    private static func libraryPass(store: LibraryStore, positions: PositionSync) -> SyncCoordinator.Pass {
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
            download: DownloadBook(files: store, store: books),
            positions: positions
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
    private static func readerParts(
        store: LibraryStore, files: LibraryFiles, firebase: Bool, positions: PositionSync?
    ) -> (importer: BookImporter, makeReader: (String) -> ReaderViewModel)? {
        #if os(iOS)
        let loader = PublicationLoader()
        let bookStore: BookFileStore = firebase ? FirebaseBookStore() : OfflineBookStore()
        let source = EpubBookSource(store: store, files: files, loader: loader, download: DownloadBook(files: store, store: bookStore))
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
            EpubImporter(store: store, files: files, loader: loader),
            { bookId in ReaderViewModel(bookId: bookId, source: source, positions: store, settings: settings, sync: readerSync) }
        )
        #else
        return nil
        #endif
    }

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
