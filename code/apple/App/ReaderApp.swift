import SwiftUI

@main
struct ReaderApp: App {
    @State private var home: HomeViewModel
    @State private var library: LibraryViewModel

    init() {
        AppFont.register()
        let viewModels = AppGraph.makeViewModels()
        _home = State(initialValue: viewModels.home)
        _library = State(initialValue: viewModels.library)
    }

    var body: some Scene {
        WindowGroup {
            RootView(home: home, library: library)
                .windowMinimumSize()
                #if os(iOS)
                // La paleta del diseño es clara y no hay tema oscuro: la barra de estado va oscura (IOS-002).
                .preferredColorScheme(.light)
                #endif
        }
        #if os(macOS)
        .defaultSize(WindowLayout.defaultSize)
        #endif
    }
}
