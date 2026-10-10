import ReaderDomain
import SwiftUI

/// Pantalla raíz. En iPhone, Inicio, Buscar y la biblioteca (Estantes) con la barra inferior flotante (HOM-005);
/// en la Mac, sin barra hasta que su navegación tenga diseño (K-078): la biblioteca se abre desde Inicio y se vuelve
/// atrás.
struct RootView: View {
    let home: HomeViewModel
    let search: SearchViewModel
    let library: LibraryViewModel
    @State private var tabs = TabBarModel()
    #if os(macOS)
    @State private var path: [LibraryFilter] = []
    #endif

    var body: some View {
        content
            // La sincronización arranca con la app, sin importar qué pantalla se ve primero (SYN-001).
            // Cada pantalla vuelve a leer la base al aparecer y cuando la sincronización la cambia.
            .task { await home.load() }
    }

    @ViewBuilder
    private var content: some View {
        #if os(iOS)
        destination
            // La barra va en el área segura de abajo: el scroll pasa por debajo y deja su alto al final.
            .safeAreaInset(edge: .bottom, spacing: 0) {
                MarginTabBar(selected: tabs.selected, onSelect: tabs.select)
                    // El aviso cuelga hacia arriba desde el borde de la barra, sin cambiar el alto del área segura.
                    .overlay(alignment: .top) {
                        Color.clear.frame(height: 0).overlay(alignment: .bottom) {
                            if tabs.showsComingSoon {
                                ComingSoonNotice()
                                    .fixedSize()
                                    .padding(.bottom, 12)
                                    .transition(.opacity)
                            }
                        }
                    }
                    .animation(.easeOut(duration: 0.2), value: tabs.showsComingSoon)
            }
            .onChange(of: tabs.showsComingSoon) { _, shows in
                if shows { AccessibilityNotification.Announcement(String(localized: "Llega más adelante.")).post() }
            }
        #else
        NavigationStack(path: $path) {
            HomeView(viewModel: home, onOpenLibrary: openLibrary)
                .navigationDestination(for: LibraryFilter.self) { _ in LibraryView(viewModel: library) }
        }
        #endif
    }

    #if os(iOS)
    @ViewBuilder
    private var destination: some View {
        switch tabs.selected {
        case .search: SearchView(viewModel: search)
        case .shelves: LibraryView(viewModel: library, onImport: tabs.showComingSoon)
        default: HomeView(viewModel: home, onOpenLibrary: openLibrary)
        }
    }
    #endif

    /// HOM-011: la biblioteca abre con el filtro de la sección de Inicio.
    private func openLibrary(_ filter: LibraryFilter) {
        library.select(filter)
        #if os(iOS)
        tabs.select(.shelves)
        #else
        path = [filter]
        #endif
    }
}
