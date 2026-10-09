import SwiftUI

@main
struct ReaderApp: App {
    // Prueba de Inicio (K-076): la biblioteca es falsa hasta que exista la importación.
    @State private var home = HomeViewModel(library: FakeLibraryRepository())

    init() {
        AppFont.register()
    }

    var body: some Scene {
        WindowGroup {
            RootView(home: home)
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
