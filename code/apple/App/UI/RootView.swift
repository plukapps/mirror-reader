import SwiftUI

/// Pantalla raíz. En iPhone, Inicio con la barra inferior flotante (HOM-005); en la Mac, sin barra
/// hasta que su navegación tenga diseño (K-078).
struct RootView: View {
    let home: HomeViewModel
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
        HomeView(viewModel: home)
        #endif
    }
}
