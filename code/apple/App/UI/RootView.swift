import ReaderDomain
import SwiftUI
import UniformTypeIdentifiers

/// Pantalla raíz. En iPhone, Inicio, Buscar y la biblioteca (Estantes) con la barra inferior flotante (HOM-005), y el
/// lector a pantalla completa (RDR-007); en la Mac, sin barra hasta que su navegación tenga diseño (K-078): la
/// biblioteca se abre desde Inicio y se vuelve atrás, y no hay lector (ADR 0013).
struct RootView: View {
    let home: HomeViewModel
    let search: SearchViewModel
    let library: LibraryViewModel
    let sync: SyncCoordinator
    let makeReader: ((String) -> ReaderViewModel)?
    @Environment(\.scenePhase) private var scenePhase
    @State private var tabs = TabBarModel()
    /// LIB-001: selector de Archivos, desde Inicio o desde la biblioteca.
    @State private var picking = false
    #if os(macOS)
    @State private var path: [LibraryFilter] = []
    #endif

    var body: some View {
        content
            // La sincronización arranca con la app, sin importar qué pantalla se ve primero (SYN-001).
            // Cada pantalla vuelve a leer la base al aparecer y cuando la sincronización la cambia.
            .task {
                await home.load()
                sync.request()
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
    }

    @ViewBuilder
    private var content: some View {
        #if os(iOS)
        destination
            // La barra va en el área segura de abajo: el scroll pasa por debajo y deja su alto al final.
            .safeAreaInset(edge: .bottom, spacing: 0) {
                MarginTabBar(selected: tabs.selected, onSelect: tabs.select)
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
            .fullScreenCover(item: readerBinding) { opened in
                if let makeReader {
                    ReaderView(viewModel: makeReader(opened.id)) {
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
        case .shelves: LibraryView(viewModel: library, onImport: importAction ?? tabs.showComingSoon, onOpen: home.open(bookId:))
        default: HomeView(viewModel: home, onOpenLibrary: openLibrary, onImport: importAction)
        }
    }
    #endif

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
            .background(MarginColors.ink, in: RoundedRectangle(cornerRadius: 14))
            .padding(.horizontal, 16)
            .task {
                AccessibilityNotification.Announcement(text).post()
                try? await Task.sleep(for: .seconds(3))
                onTimeout()
            }
    }
}
