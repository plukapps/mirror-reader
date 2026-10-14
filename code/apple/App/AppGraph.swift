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
        /// Perfil (PRF-001): cuenta, espacio y ajustes.
        let profile: ProfileViewModel
        /// Menú de un libro con mantener presionado (LIB-016).
        let bookActions: BookActionsViewModel
        /// nil en la Mac: Readium Swift solo soporta iOS (ADR 0013).
        let makeReader: ((_ bookId: String) -> ReaderViewModel)?
        /// Planes (PLN-002): quién lo abre y su estado.
        let plans: PlansPresenter
        /// Apariencia de la app (PRF-021).
        let appearance: AppearanceSettings
        /// La red del dispositivo para "Sincronizar solo con Wi-Fi" (PRF-023). Se guarda para que siga avisando.
        let network: PathNetworkMonitor
        /// Pantalla de arranque (ONB-019).
        let start: AppStartViewModel
        /// nil sin Firebase: no hay cuenta que crear (ADR 0015).
        let onboarding: OnboardingDependencies?
        /// Dominio de los enlaces de los emails de la cuenta (`<proyecto>.firebaseapp.com`), o nil sin Firebase.
        let authLinkHost: String?
    }

    static func makeScreens() -> Screens {
        let files = LibraryFiles.standard
        let store = LibraryStore(container: container(), files: files)
        // Los tests corren dentro de la app: nunca tocan el proyecto real.
        let firebase = !isRunningTests && FirebaseSetup.configureIfAvailable()
        // PRF-023: "Sincronizar solo con Wi-Fi" mira la red real.
        let gate = SyncNetworkGate()
        let network = PathNetworkMonitor()
        network.start { type in Task { @MainActor in gate.networkChanged(type) } }
        let cloudAllowed: @Sendable () async -> Bool = { await gate.allowsSync }
        let positions = firebase ? positionSync(store: store) : nil
        // PRF-006, PLN-009: la última cuota leída, que cada pasada actualiza. De ella sale el plan (PLN-014, PLN-015).
        let quotaCache = UserDefaultsQuotaCache()
        // LIB-020: quitar un libro lo borra de la cuenta y de los otros dispositivos.
        let removals = firebase ? RemovalSync(remote: FirebaseRemovals(), local: store, account: FirebaseAccountRepository()) : nil
        #if os(iOS)
        // LIB-012: un libro bajado de la nube saca la portada del EPUB si todavía no la tiene, como Android.
        let loader = PublicationLoader()
        let importer = EpubImporter(store: store, files: files, loader: loader)
        let bookFiles: BookFiles = CoverExtractingBookFiles(store: store, importer: importer)
        let reader: (importer: BookImporter, makeReader: (String) -> ReaderViewModel)? = readerParts(
            store: store, files: files, loader: loader, importer: importer, bookFiles: bookFiles, firebase: firebase, positions: positions,
            cloudAllowed: cloudAllowed, quotaCache: quotaCache
        )
        #else
        let bookFiles: BookFiles = store
        let reader: (importer: BookImporter, makeReader: (String) -> ReaderViewModel)? = nil
        #endif

        // La sincronización vuelve a leer la base en Inicio y en la biblioteca cada vez que la cambia.
        let refs = ScreenRefs()
        let pass = positions.map {
            libraryPass(store: store, bookFiles: bookFiles, positions: $0, removals: removals, quotaCache: quotaCache)
        }
        // SYN-015: un libro agregado en otro dispositivo pide una pasada (la app a la vista).
        let watcher = firebase
            ? LibraryWatcher(remote: FirestoreRemoteLibrary(), known: store, account: FirebaseAccountRepository()) {
                await MainActor.run { refs.sync?.request() }
            }
            : nil
        let sync = SyncCoordinator(pass: pass, positions: positions, removals: removals, watcher: watcher, gate: gate) {
            await refs.home?.reload()
            await refs.library?.load()
        }
        let library = LibraryViewModel(library: store, sync: sync, quotaCache: quotaCache, usageNotice: UserDefaultsUsageNotice())
        let home = HomeViewModel(
            library: store, importer: reader?.importer, quotaCheck: ImportQuotaCheck(uploads: store, cache: quotaCache)
        ) { sync.request() }
        refs.home = home
        refs.library = library
        refs.sync = sync
        let search = SearchViewModel(library: store)
        let bookActions = BookActionsViewModel(
            // La posición sale en cuanto se pueda, sin esperar la red (SYN-011).
            actions: BookActions(
                positions: store,
                editor: store,
                requestFlush: { Task { await sync.flushPositions() } },
                requestRemovalPush: { Task { await sync.pushRemovals() } }
            ),
            reload: {
                await home.reload()
                await library.load()
                await search.load()
            },
            notify: { [weak home] in home?.show($0) }
        )
        let account = firebase ? FirebaseAccountRepository() : nil
        let appearance = AppearanceSettings(repository: UserDefaultsAppearance())
        return Screens(
            home: home,
            search: search,
            library: library,
            sync: sync,
            profile: ProfileViewModel(
                account: account,
                quota: firebase ? FirestoreQuotaSource() : nil,
                cache: quotaCache,
                deviceName: UserDefaultsSyncPreferences.model,
                settings: UserDefaultsReaderSettings(),
                passwords: account,
                appearance: appearance,
                wifiOnly: gate,
                signOut: account.map { account in
                    SignOut(
                        sync: CoordinatorSyncControl(coordinator: sync),
                        localData: LocalAccountWiper(store: store, files: files, defaults: .standard),
                        session: account,
                        reminders: NotificationReminderScheduler(),
                        hasPending: {
                            let uploads = (try? await store.pending()) ?? []
                            let positions = await store.pendingPositions()
                            let removals = await store.pendingCloudRemovals()
                            return !uploads.isEmpty || !positions.isEmpty || !removals.isEmpty
                        }
                    )
                }
            ),
            bookActions: bookActions,
            makeReader: reader?.makeReader,
            plans: PlansPresenter(cache: quotaCache),
            appearance: appearance,
            network: network,
            start: AppStartViewModel(account: account),
            onboarding: account.map { account in
                OnboardingDependencies(
                    auth: account,
                    account: account,
                    preferences: UserDefaultsOnboardingPreferences(),
                    scheduler: NotificationReminderScheduler(),
                    google: GoogleSignInClient.signIn
                )
            },
            authLinkHost: firebase ? FirebaseSetup.authDomain() : nil
        )
    }

    /// Una pasada de sincronización de la biblioteca con Firebase (SYN-001), como `SyncLibraryUseCase` de Android.
    private static func libraryPass(
        store: LibraryStore, bookFiles: BookFiles, positions: PositionSync, removals: RemovalSync?, quotaCache: QuotaCache
    ) -> SyncCoordinator.Pass {
        let remote = FirestoreRemoteLibrary()
        let books = FirebaseBookStore()
        let covers = FirebaseCoverStore()
        let sync = LibrarySync(
            account: FirebaseAccountRepository(),
            remote: remote,
            covers: covers,
            cloud: store,
            upload: UploadBooks(uploads: store, files: books, covers: covers, library: remote, quota: FirestoreQuotaSource(), cache: quotaCache),
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
        firebase: Bool, positions: PositionSync?, cloudAllowed: @escaping @Sendable () async -> Bool, quotaCache: QuotaCache
    ) -> (importer: BookImporter, makeReader: (String) -> ReaderViewModel) {
        let bookStore: BookFileStore = firebase ? FirebaseBookStore() : OfflineBookStore()
        // PRF-023: un libro solo en la nube no se baja sin Wi-Fi si así se eligió.
        let download = DownloadBook(files: bookFiles, store: bookStore, allowed: cloudAllowed)
        let source = EpubBookSource(store: store, files: files, loader: loader, download: download)
        let settings = UserDefaultsReaderSettings()
        let readerSync = positions.map { positions in
            CloudReaderSync(
                resolver: ResolveOpeningPosition(
                    remote: FirestorePositions(), store: store, positions: store, account: FirebaseAccountRepository()
                ),
                positions: positions,
                allowed: cloudAllowed
            )
        }
        return (
            importer,
            { bookId in
                ReaderViewModel(
                    bookId: bookId, source: source, positions: store, settings: settings, sync: readerSync,
                    plan: { PlanCatalog.planOf(quotaCache.last()) }
                )
            }
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
