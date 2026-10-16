import PhotosUI
import ReaderDomain
import SwiftUI
import UniformTypeIdentifiers

/// Pantalla raíz. En iPhone, Inicio, Buscar, la biblioteca (Estantes) y Perfil con la barra inferior flotante (HOM-005), y el
/// lector a pantalla completa (RDR-007); en la Mac, sin barra hasta que su navegación tenga diseño (K-078): la
/// biblioteca se abre desde Inicio y se vuelve atrás, y no hay lector (ADR 0013).
struct RootView: View {
    let home: HomeViewModel
    let search: SearchViewModel
    let library: LibraryViewModel
    let sync: SyncCoordinator
    let profile: ProfileViewModel
    @Bindable var bookActions: BookActionsViewModel
    let makeReader: ((_ bookId: String, _ noteId: String?) -> ReaderViewModel)?
    /// LIB-022: el detalle de un libro.
    let makeDetail: (_ bookId: String) -> BookDetailViewModel
    /// P01 Planes (PLN-002), a pantalla completa sobre todo lo demás.
    @Bindable var plans: PlansPresenter
    /// ONB-013: al llegar desde "Importar mis libros" abre el selector de Archivos.
    var importOnAppear = false
    var onImportOpened: () -> Void = {}
    /// GST-021, GST-022: al volver del alta corta o del ingreso, la Biblioteca.
    var libraryOnAppear = false
    var onLibraryOpened: () -> Void = {}
    /// PRF-009: la sesión se cerró desde Perfil.
    var onSignedOut: () -> Void = {}
    /// GST-011: el alta corta, desde el banner, el aviso o Perfil.
    var onCreateAccount: () -> Void = {}
    /// GST-022: el ingreso, desde Perfil.
    var onSignIn: () -> Void = {}
    @Environment(\.scenePhase) private var scenePhase
    @State private var tabs = TabBarModel()
    /// LIB-001: selector de Archivos, desde Inicio o desde la biblioteca.
    @State private var picking = false
    #if os(macOS)
    @State private var path: [LibraryFilter] = []
    #endif
    /// LIB-019: la foto elegida en el selector.
    @State private var coverPhoto: PhotosPickerItem?

    var body: some View {
        content
            // La sincronización arranca con la app, sin importar qué pantalla se ve primero (SYN-001).
            // Cada pantalla vuelve a leer la base al aparecer y cuando la sincronización la cambia.
            .task {
                if importOnAppear {
                    onImportOpened()
                    importAction?()
                }
                #if os(iOS)
                if libraryOnAppear {
                    onLibraryOpened()
                    tabs.select(.shelves)
                }
                #endif
                await home.load()
                sync.request()
                // Al llegar desde el onboarding la app ya estaba activa: la posición empieza a escucharse ahora (SYN-012).
                sync.appBecameActive()
            }
            // SYN-001: al volver a la app, sincroniza si la última pasada buena tiene más de 5 minutos.
            // SYN-011, SYN-012: la posición se escucha mientras la app se ve y lo pendiente sale al dejar de verse.
            .onChange(of: scenePhase) { _, phase in
                switch phase {
                case .active: sync.appBecameActive()
                case .background: sync.appWentToBackground()
                default: break
                }
            }
            .fileImporter(isPresented: $picking, allowedContentTypes: [.epub], allowsMultipleSelection: true) { result in
                if case let .success(urls) = result {
                    Task {
                        await home.importBooks(urls)
                        await library.load()
                    }
                }
            }
            // P02 (PLN-007 a PLN-009): el libro importado no entra en la nube.
            .sheet(
                item: Binding(get: { home.quotaBlock }, set: { if $0 == nil { home.dismissQuotaBlock() } }),
                onDismiss: plans.sheetDismissed
            ) { notice in
                QuotaBlockSheet(
                    notice: notice,
                    onSeePlans: {
                        plans.openPlansAfterSheet()
                        home.dismissQuotaBlock()
                    },
                    onNotNow: home.dismissQuotaBlock
                )
            }
            // LIB-020: un libro abierto no se quita aunque lo borren desde otro dispositivo; al cerrarlo, sí.
            .onChange(of: home.readerBookId) { old, new in
                if let old { sync.readerClosed(old) }
                if let new { sync.readerOpened(new) }
            }
            // LIB-016 a LIB-020: el menú de cada portada usa estas acciones.
            .environment(bookActions)
            // LIB-022: el detalle, a pantalla completa en el iPhone y como hoja en la Mac.
            .background { detailPresentation }
            .photosPicker(isPresented: $bookActions.isPickingCover, selection: $coverPhoto, matching: .images)
            .onChange(of: coverPhoto) { _, item in
                guard let item else { return }
                coverPhoto = nil
                Task {
                    if let data = try? await item.loadTransferable(type: Data.self) {
                        await bookActions.setCover(data)
                    } else {
                        bookActions.coverFailed()
                    }
                }
            }
            .alert(
                removeTitle,
                isPresented: Binding(get: { bookActions.removing != nil }, set: { if !$0 { bookActions.cancelRemove() } }),
                presenting: bookActions.removing
            ) { book in
                Button("Cancelar", role: .cancel) { bookActions.cancelRemove() }
                Button("Quitar", role: .destructive) { Task { await bookActions.confirmRemove(book) } }
            } message: { _ in
                // GST-008: sin cuenta, solo este dispositivo (L3).
                Text(library.signedIn
                    ? "Se borra de tu cuenta y de todos tus dispositivos, con su progreso. Podés volver a importarlo."
                    : "Se borra el libro de este dispositivo, con su progreso y sus notas.")
            }
    }

    private var removeTitle: String {
        bookActions.removing.map { String(localized: "¿Quitar «\($0.title)»?") } ?? ""
    }

    @ViewBuilder
    private var content: some View {
        #if os(iOS)
        destination
            // La barra va en el área segura de abajo: el scroll pasa por debajo y deja su alto al final.
            .safeAreaInset(edge: .bottom, spacing: 0) {
                Group {
                    if tabs.barHidden {
                        Color.clear.frame(height: 0)
                    } else {
                        MarginTabBar(selected: tabs.selected, onSelect: tabs.select)
                    }
                }
                    // Los avisos cuelgan hacia arriba desde el borde de la barra, sin cambiar el alto del área segura.
                    .overlay(alignment: .top) {
                        Color.clear.frame(height: 0).overlay(alignment: .bottom) {
                            VStack(spacing: 8) {
                                if let message = home.messages.first {
                                    HomeMessage(text: message, onTimeout: home.dismissMessage)
                                        .id(message)
                                }
                                if tabs.showsComingSoon {
                                    ComingSoonNotice()
                                        .fixedSize()
                                        .transition(.opacity)
                                }
                            }
                            .padding(.bottom, 12)
                        }
                    }
                    .animation(.easeOut(duration: 0.2), value: tabs.showsComingSoon)
            }
            .onChange(of: tabs.showsComingSoon) { _, shows in
                if shows { AccessibilityNotification.Announcement(String(localized: "Llega más adelante.")).post() }
            }
            .background {
                // Va en su propia vista: dos presentaciones a pantalla completa no comparten la misma.
                Color.clear.fullScreenCover(isPresented: $plans.plansOpen) {
                    PlansScreen(presenter: plans, tabs: tabs, onClose: plans.closePlans)
                }
            }
            .fullScreenCover(item: readerBinding) { opened in
                if let makeReader {
                    ReaderView(viewModel: makeReader(opened.id, nil), plans: plans, tabs: tabs) {
                        home.closeReader()
                        Task {
                            await home.reload()
                            await library.load()
                        }
                    }
                }
            }
        #else
        NavigationStack(path: $path) {
            HomeView(viewModel: home, onOpenLibrary: openLibrary)
                .navigationDestination(for: LibraryFilter.self) { _ in
                    LibraryView(viewModel: library, onOpen: home.open(bookId:))
                }
        }
        .overlay(alignment: .bottom) {
            if let message = home.messages.first {
                HomeMessage(text: message, onTimeout: home.dismissMessage).id(message).padding(.bottom, 24)
            }
        }
        #endif
    }

    #if os(iOS)
    @ViewBuilder
    private var destination: some View {
        switch tabs.selected {
        case .search: SearchView(viewModel: search, onOpen: home.open(bookId:))
        case .shelves: LibraryView(
            viewModel: library, onImport: importAction ?? tabs.showComingSoon, onOpen: home.open(bookId:),
            onSeePlans: plans.openPlans, onCreateAccount: onCreateAccount
        )
        case .profile:
            ProfileView(
                viewModel: profile,
                actions: ProfileActions(
                    comingSoon: tabs.showComingSoon,
                    openPlans: plans.openPlans,
                    openPlansAfterSheet: plans.openPlansAfterSheet,
                    sheetDismissed: plans.sheetDismissed,
                    notify: { home.show($0) },
                    setTabBarHidden: { tabs.barHidden = $0 },
                    createAccount: onCreateAccount,
                    signIn: onSignIn
                ),
                onSignedOut: onSignedOut
            )
        default: HomeView(
            viewModel: home, onOpenLibrary: openLibrary, onImport: importAction,
            onOpenProfile: { tabs.select(.profile) }, onCreateAccount: onCreateAccount
        )
        }
    }
    #endif

    @ViewBuilder
    private var detailPresentation: some View {
        // Va en su propia vista: dos presentaciones a pantalla completa no comparten la misma.
        #if os(iOS)
        Color.clear.fullScreenCover(item: $bookActions.detailBook) { book in detail(book) }
        #else
        Color.clear.sheet(item: $bookActions.detailBook) { book in detail(book).frame(minWidth: 420, minHeight: 600) }
        #endif
    }

    private func detail(_ book: LibraryBook) -> some View {
        BookDetailView(viewModel: makeDetail(book.id), makeReader: makeReader) {
            bookActions.detailBook = nil
            Task {
                await home.reload()
                await library.load()
            }
        }
    }

    /// Abre el selector de Archivos, o nil donde no se importa (la Mac, ADR 0013).
    private var importAction: (() -> Void)? {
        home.canImport ? { picking = true } : nil
    }

    /// HOM-011: la biblioteca abre con el filtro de la sección de Inicio.
    private func openLibrary(_ filter: LibraryFilter) {
        library.select(filter)
        #if os(iOS)
        tabs.select(.shelves)
        #else
        path = [filter]
        #endif
    }

    private var readerBinding: Binding<OpenBook?> {
        Binding(
            get: { home.readerBookId.map(OpenBook.init) },
            set: { if $0 == nil { home.closeReader() } }
        )
    }
}

private struct OpenBook: Identifiable {
    let id: String
}

/// Aviso breve (resultado de importar, LIB-001), como el snackbar de Android.
private struct HomeMessage: View {
    let text: String
    let onTimeout: () -> Void

    var body: some View {
        Text(text)
            .font(.app(14, .medium))
            .foregroundStyle(.white)
            .multilineTextAlignment(.center)
            .padding(.horizontal, 16)
            .padding(.vertical, 10)
            .background(MarginColors.inkFill, in: RoundedRectangle(cornerRadius: 14))
            .padding(.horizontal, 16)
            .task {
                AccessibilityNotification.Announcement(text).post()
                try? await Task.sleep(for: .seconds(3))
                onTimeout()
            }
    }
}
