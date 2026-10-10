import SwiftUI

@main
struct ReaderApp: App {
    @State private var screens: AppGraph.Screens

    init() {
        AppFont.register()
        _screens = State(initialValue: AppGraph.makeScreens())
    }

    var body: some Scene {
        WindowGroup {
            RootView(home: screens.home, search: screens.search, library: screens.library)
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
