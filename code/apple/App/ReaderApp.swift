import SwiftUI

@main
struct ReaderApp: App {
    @State private var app: AppDependencies

    init() {
        AppFont.register()
        _app = State(initialValue: AppGraph.make())
    }

    var body: some Scene {
        WindowGroup {
            RootView(home: app.home, makeReader: app.makeReader)
                .windowMinimumSize()
                #if os(iOS)
                // La paleta del diseño es clara y no hay tema oscuro: la barra de estado va oscura (IOS-002).
                .preferredColorScheme(.light)
                #endif
                // "Abrir con" desde otra app, o un EPUB tocado en Archivos (LIB-001).
                .onOpenURL { url in Task { await app.home.openIncoming(url) } }
        }
        #if os(macOS)
        .defaultSize(WindowLayout.defaultSize)
        #endif
    }
}
