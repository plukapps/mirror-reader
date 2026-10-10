import Foundation
import ReaderDomain
import OSLog
import SwiftData

/// Lo que la app arma al arrancar: Inicio y, donde hay motor de EPUB (iOS, ADR 0013), cómo abrir el lector.
@MainActor
struct AppDependencies {
    let home: HomeViewModel
    /// nil en la Mac: Readium Swift solo soporta iOS.
    let makeReader: ((_ bookId: String) -> ReaderViewModel)?
}

/// Arma las dependencias de la app (ADR 0010: sin framework de inyección, por inicializador).
@MainActor
enum AppGraph {
    static func make() -> AppDependencies {
        let files = LibraryFiles.standard
        let store = LibraryStore(container: container(), files: files)
        // Los tests corren dentro de la app: nunca tocan el proyecto real.
        let firebase = !isRunningTests && FirebaseSetup.configureIfAvailable()
        let reader = readerParts(store: store, files: files, firebase: firebase)
        guard firebase else {
            // Sin configuración de Firebase la app funciona solo en local (ADR 0002).
            return AppDependencies(home: HomeViewModel(library: store, importer: reader?.importer), makeReader: reader?.makeReader)
        }
        let remote = FirestoreRemoteLibrary()
        let sync = LibrarySync(
            account: FirebaseAccountRepository(),
            credentials: FirebaseSetup.devCredentials(),
            remote: remote,
            positions: remote,
            covers: FirebaseCoverStore(),
            cloud: store
        )
        let log = Logger(subsystem: "com.pluk.reader", category: "LibrarySync")
        let home = HomeViewModel(library: store, importer: reader?.importer) { onChange in
            let outcome = await sync.run(onChange: onChange)
            log.info("Sincronización: \(String(describing: outcome), privacy: .public)")
            return outcome
        }
        return AppDependencies(home: home, makeReader: reader?.makeReader)
    }

    private static func readerParts(
        store: LibraryStore, files: LibraryFiles, firebase: Bool
    ) -> (importer: BookImporter, makeReader: (String) -> ReaderViewModel)? {
        #if os(iOS)
        let loader = PublicationLoader()
        let bookStore: BookFileStore = firebase ? FirebaseBookStore() : OfflineBookStore()
        let source = EpubBookSource(store: store, files: files, loader: loader, download: DownloadBook(files: store, store: bookStore))
        let settings = UserDefaultsReaderSettings()
        return (
            EpubImporter(store: store, files: files, loader: loader),
            { bookId in ReaderViewModel(bookId: bookId, source: source, positions: store, settings: settings) }
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

/// Sin Firebase no hay de dónde bajar libros: un libro solo en la nube no se puede abrir (ADR 0002).
private struct OfflineBookStore: BookFileStore {
    func downloadBook(bookId: String, to destination: URL) async throws {
        throw RemoteUnavailableError("Sin configuración de Firebase.")
    }
}
