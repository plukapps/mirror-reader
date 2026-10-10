import Foundation
import ReaderDomain
import OSLog
import SwiftData

/// Arma las dependencias de la app (ADR 0010: sin framework de inyección, por inicializador).
@MainActor
enum AppGraph {
    /// Inicio y la biblioteca leen la misma base local.
    static func makeViewModels() -> (home: HomeViewModel, library: LibraryViewModel) {
        let store = LibraryStore(container: container(), files: .standard)
        let library = LibraryViewModel(library: store)
        // Los tests corren dentro de la app: nunca tocan el proyecto real.
        guard !isRunningTests, FirebaseSetup.configureIfAvailable() else {
            // Sin configuración de Firebase la app funciona solo en local (ADR 0002).
            return (HomeViewModel(library: store), library)
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
        return (home, library)
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
