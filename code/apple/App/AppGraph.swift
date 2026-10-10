import Foundation
import ReaderDomain
import OSLog
import SwiftData

/// Arma las dependencias de la app (ADR 0010: sin framework de inyección, por inicializador).
@MainActor
enum AppGraph {
    /// ViewModels de las pantallas principales, sobre la misma base local.
    struct Screens {
        let home: HomeViewModel
        let search: SearchViewModel
        let library: LibraryViewModel
    }

    static func makeScreens() -> Screens {
        let store = LibraryStore(container: container(), files: .standard)
        let library = LibraryViewModel(library: store)
        return Screens(home: makeHome(store: store, library: library), search: SearchViewModel(library: store), library: library)
    }

    /// La sincronización vuelve a leer la base en Inicio y en la biblioteca cada vez que la cambia.
    private static func makeHome(store: LibraryStore, library: LibraryViewModel) -> HomeViewModel {
        // Los tests corren dentro de la app: nunca tocan el proyecto real.
        guard !isRunningTests, FirebaseSetup.configureIfAvailable() else {
            // Sin configuración de Firebase la app funciona solo en local (ADR 0002).
            return HomeViewModel(library: store)
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
        let home = HomeViewModel(library: store) { onChange in
            // Cada cambio de la base recarga Inicio y la biblioteca.
            let outcome = await sync.run {
                await onChange()
                await library.load()
            }
            log.info("Sincronización: \(String(describing: outcome), privacy: .public)")
            return outcome
        }
        return home
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
