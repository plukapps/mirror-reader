import SwiftUI

@main
struct ReaderApp: App {
    @State private var home: HomeViewModel

    init() {
        AppFont.register()
        _home = State(initialValue: AppGraph.makeHome())
    }

    var body: some Scene {
        WindowGroup {
            HomeView(viewModel: home)
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
