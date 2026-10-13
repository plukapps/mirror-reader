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
            AppRootView(screens: screens)
                .windowMinimumSize()
                #if os(macOS)
                // La Mac todavía no llega a Perfil (K-078): sigue clara, como su diseño.
                .preferredColorScheme(.light)
                #endif
        }
        #if os(macOS)
        .defaultSize(WindowLayout.defaultSize)
        #endif
    }
}
