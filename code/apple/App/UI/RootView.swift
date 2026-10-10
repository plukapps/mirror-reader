import SwiftUI

/// Pantalla raíz. En iPhone, Inicio con la barra inferior flotante (HOM-005) y el lector a pantalla completa
/// (RDR-007); en la Mac, sin barra hasta que su navegación tenga diseño (K-078) y sin lector (ADR 0013).
struct RootView: View {
    let home: HomeViewModel
    let makeReader: ((String) -> ReaderViewModel)?
    @State private var tabs = TabBarModel()

    var body: some View {
        #if os(iOS)
        HomeView(viewModel: home)
            // La barra va en el área segura de abajo: el scroll pasa por debajo y deja su alto al final.
            .safeAreaInset(edge: .bottom, spacing: 0) {
                MarginTabBar(selected: tabs.selected, onSelect: tabs.select)
                    // El aviso cuelga hacia arriba desde el borde de la barra, sin cambiar el alto del área segura.
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
                        Task { await home.refresh() }
                    }
                }
            }
        #else
        HomeView(viewModel: home)
            .overlay(alignment: .bottom) {
                if let message = home.messages.first {
                    HomeMessage(text: message, onTimeout: home.dismissMessage).id(message).padding(.bottom, 24)
                }
            }
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

/// Aviso breve de Inicio (resultado de importar, LIB-001), como el snackbar de Android.
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
