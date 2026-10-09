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
            HomeView(viewModel: home)
                .frame(minWidth: 480, minHeight: 480)
        }
        .defaultSize(width: 720, height: 900)
    }
}
