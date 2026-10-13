import ReaderDomain
import SwiftUI
#if os(iOS)
import UIKit
#endif

/// Lo que se puede tocar en Perfil. Lo que todavía no existe va a `comingSoon` (PRF-003).
struct ProfileActions {
    var comingSoon: () -> Void = {}
    var signOut: (() -> Void)?
    var openFaq: ((FaqId) -> Void)?
    var openAllFaq: (() -> Void)?
}


/// Perfil: cuenta, espacio, ajustes y ayuda (PRF-001 a PRF-019). Diseño: "09 — Profile".
struct ProfileView: View {
    let viewModel: ProfileViewModel
    var actions = ProfileActions()
    /// PRF-009: con la sesión cerrada, a la bienvenida.
    var onSignedOut: () -> Void = {}
    @State private var fontSheetOpen = false
    /// PRF-017, PRF-018: las preguntas frecuentes se abren dentro del tab.
    @State private var path: [ProfileRoute] = []

    var body: some View {
        NavigationStack(path: $path) {
            ScrollView {
                ProfileContent(viewModel: viewModel, actions: contentActions, onOpenFont: { fontSheetOpen = true })
                    .frame(maxWidth: WindowLayout.contentMaxWidth)
                    .frame(maxWidth: .infinity)
            }
            .background(MarginColors.paper)
            .toolbar(.hidden)
            .navigationDestination(for: ProfileRoute.self) { route in
                switch route {
                case let .faq(id): FaqAnswerView(id: id, onBack: { path.removeLast() })
                case .allFaq: FaqListView(onBack: { path.removeLast() }, onOpen: { path.append(.faq($0)) })
                }
            }
        }
        .task { await viewModel.load() }
        // PRF-013: el panel de letra, claro, con la misma forma que el del lector.
        .sheet(isPresented: $fontSheetOpen) {
            FontSheet(viewModel: viewModel)
                .presentationDetents([.height(200)])
                .presentationCornerRadius(28)
                .presentationBackground(MarginColors.field)
                .presentationDragIndicator(.visible)
        }
        .alert(
            "¿Cerrar sesión?",
            isPresented: Binding(get: { viewModel.signOutDialog != nil }, set: { if !$0 { viewModel.dismissSignOut() } }),
            presenting: viewModel.signOutDialog
        ) { _ in
            Button("Cancelar", role: .cancel) { viewModel.dismissSignOut() }
            Button("Cerrar sesión", role: .destructive) { Task { await viewModel.confirmSignOut() } }
        } message: { dialog in
            Text(
                dialog.hasPendingChanges
                    ? "Hay libros o progreso de lectura que todavía no se subieron. Si cerrás sesión ahora, se pierden."
                    : "Tus libros siguen en tu cuenta. En este dispositivo se borran hasta que vuelvas a entrar."
            )
        }
        .alert("No se pudo cerrar sesión. Probá de nuevo.", isPresented: Binding(
            get: { viewModel.signOutFailed }, set: { viewModel.signOutFailed = $0 }
        )) {
            Button("Aceptar", role: .cancel) {}
        }
        .onChange(of: viewModel.signedOut) { _, signedOut in
            if signedOut { onSignedOut() }
        }
    }

    /// Sin cuenta (sin Firebase), "Cerrar sesión" avisa como lo que todavía no existe.
    private var contentActions: ProfileActions {
        var actions = actions
        actions.openFaq = { path.append(.faq($0)) }
        actions.openAllFaq = { path.append(.allFaq) }
        if viewModel.canSignOut { actions.signOut = { Task { await viewModel.requestSignOut() } } }
        return actions
    }
}

/// El contenido de Perfil, sin desplazamiento: se puede dibujar entero (por ejemplo, para compararlo con el diseño).
struct ProfileContent: View {
    let viewModel: ProfileViewModel
    var actions = ProfileActions()
    var onOpenFont: () -> Void = {}
    var version: String = Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? ""

    var body: some View {
        VStack(spacing: 0) {
            header
            ProfileSectionTitle(text: "Cuenta")
            accountCard
            ProfileSectionTitle(text: "Ajustes de lectura", bottom: 4)
            Text("Se aplican a todos tus libros.")
                .font(.app(12))
                .lineSpacing(12 * 0.2)
                .foregroundStyle(MarginColors.muted)
                .padding(EdgeInsets(top: 0, leading: 24, bottom: 10, trailing: 24))
                .frame(maxWidth: .infinity, alignment: .leading)
            ReadingDefaultsCard(viewModel: viewModel, onOpenFont: onOpenFont)
            ProfileSectionTitle(text: "App")
            appCard
            ProfileSectionTitle(text: "Ayuda")
            helpCard
            footer
        }
        .background(MarginColors.paper)
    }

    // MARK: Cabecera (PRF-005)

    private var header: some View {
        Button(action: actions.comingSoon) {
            HStack(spacing: 14) {
                ProfileAvatar(initial: viewModel.initial)
                VStack(alignment: .leading, spacing: 2) {
                    Text(viewModel.name ?? viewModel.email ?? "")
                        .font(.app(20, .bold))
                        .tracking(-20 * 0.02)
                        .lineLimit(1)
                        .foregroundStyle(MarginColors.ink)
                    if viewModel.name != nil, let email = viewModel.email {
                        Text(email)
                            .font(.app(13))
                            .lineLimit(1)
                            .truncationMode(.tail)
                            .foregroundStyle(MarginColors.onYellowMuted)
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                ProfileIcon(name: "ChevronRight", size: 24)
            }
            .padding(16)
            .background(MarginColors.yellow, in: RoundedRectangle(cornerRadius: 24))
            .contentShape(RoundedRectangle(cornerRadius: 24))
        }
        .buttonStyle(.plain)
        .padding(EdgeInsets(top: 16, leading: 16, bottom: 0, trailing: 16))
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(Text("Cuenta de \([viewModel.name, viewModel.email].compactMap { $0 }.joined(separator: ", "))"))
        .accessibilityAddTraits(.isButton)
    }

    // MARK: Cuenta (PRF-006 a PRF-010)

    private var accountCard: some View {
        ProfileCard {
            storage
            CardDivider()
            Text("Dispositivos vinculados")
                .font(.app(15, .semibold))
                .foregroundStyle(MarginColors.ink)
                .padding(EdgeInsets(top: 14, leading: 16, bottom: 6, trailing: 16))
                .frame(maxWidth: .infinity, alignment: .leading)
            thisDevice
            if viewModel.canChangePassword {
                CardDivider()
                ProfileRow(action: actions.comingSoon) {
                    ProfileIcon(name: "Lock", color: MarginColors.ink2)
                    Text("Cambiar contraseña").font(.app(15, .medium)).foregroundStyle(MarginColors.ink)
                    Spacer(minLength: 0)
                    ProfileIcon(name: "ChevronRight", color: MarginColors.muted)
                }
            }
            CardDivider()
            ProfileRow(action: actions.signOut ?? actions.comingSoon) {
                ProfileIcon(name: "Logout")
                Text("Cerrar sesión").font(.app(15, .semibold)).foregroundStyle(MarginColors.ink)
                Spacer(minLength: 0)
            }
            CardDivider()
            ProfileRow(action: actions.comingSoon) {
                ProfileIcon(name: "Delete", color: MarginColors.danger)
                Text("Eliminar cuenta").font(.app(15, .semibold)).foregroundStyle(MarginColors.danger)
                Spacer(minLength: 0)
            }
        }
    }

    private var storage: some View {
        let summary = viewModel.storage
        return VStack(alignment: .leading, spacing: 10) {
            HStack(spacing: 10) {
                HStack(spacing: 8) {
                    Text("Espacio").font(.app(15, .semibold)).foregroundStyle(MarginColors.ink)
                    if summary?.isFreePlan == true { PlanChip(text: "GRATIS") }
                }
                Spacer(minLength: 0)
                if summary?.isFreePlan == true {
                    Button(action: actions.comingSoon) {
                        Text("Mejorar plan").font(.app(13, .semibold)).underline().foregroundStyle(MarginColors.ink)
                    }
                    .buttonStyle(.plain)
                }
            }
            StorageBar(fraction: summary?.fraction ?? 0)
            Group {
                if let summary {
                    HStack {
                        Text("\(summary.used) de \(summary.total) usados")
                        Spacer(minLength: 8)
                        Text("\(summary.free) libres")
                    }
                } else if viewModel.storageFailed {
                    Text("No se pudo cargar el espacio")
                } else {
                    // Mientras carga por primera vez, una línea vacía del mismo alto para que nada salte.
                    Text(" ")
                }
            }
            .font(.app(12, .medium))
            .foregroundStyle(MarginColors.muted)
        }
        .padding(16)
    }

    /// PRF-007: por ahora solo este dispositivo.
    private var thisDevice: some View {
        ProfileRow(action: nil) {
            ProfileIcon(name: isTablet ? "TabletAndroid" : "Smartphone", size: 20)
                .frame(width: 36, height: 36)
                .background(MarginColors.paper, in: RoundedRectangle(cornerRadius: 10))
            (Text(viewModel.deviceName).foregroundStyle(MarginColors.ink)
                + Text(" ") + Text("· este dispositivo").foregroundStyle(MarginColors.muted))
                .font(.app(14, .medium))
            Spacer(minLength: 0)
        }
        .accessibilityElement(children: .combine)
    }

    private var isTablet: Bool {
        #if os(iOS)
        UIDevice.current.userInterfaceIdiom == .pad
        #else
        false
        #endif
    }

    // MARK: App (PRF-016)

    private var appCard: some View {
        ProfileCard {
            VStack(alignment: .leading, spacing: 10) {
                Text("Apariencia").font(.app(15, .medium)).foregroundStyle(MarginColors.ink)
                ProfileSegmentedControl(
                    segments: [
                        ProfileSegment(id: 0, label: "Claro", icon: "LightMode"),
                        ProfileSegment(id: 1, label: "Oscuro", icon: "DarkMode"),
                        ProfileSegment(id: 2, label: "Sistema", icon: "Contrast"),
                    ],
                    // La app hoy es solo clara.
                    selected: 0,
                    onSelect: { _ in actions.comingSoon() }
                )
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 14)
            CardDivider()
            // PRF-023.
            switchRow("Sincronizar solo con Wi-Fi", "Ahorra datos móviles", isOn: viewModel.wifiOnly ?? false) {
                if let on = viewModel.wifiOnly { viewModel.setWifiOnly(!on) } else { actions.comingSoon() }
            }
            CardDivider()
            // PRF-016: todavía avisa que llega más adelante.
            switchRow("Descargar libros automáticamente", "Libros nuevos de tus otros dispositivos", isOn: false, action: actions.comingSoon)
        }
    }

    private func switchRow(
        _ title: LocalizedStringKey, _ subtitle: LocalizedStringKey, isOn: Bool, action: @escaping () -> Void
    ) -> some View {
        ProfileRow(action: action) {
            VStack(alignment: .leading, spacing: 2) {
                Text(title).font(.app(15, .medium)).foregroundStyle(MarginColors.ink)
                Text(subtitle).font(.app(12)).foregroundStyle(MarginColors.muted)
            }
            Spacer(minLength: 0)
            ProfileSwitch(isOn: isOn)
        }
        .accessibilityElement(children: .combine)
        .accessibilityValue(Text(isOn ? "activado" : "desactivado"))
    }

    // MARK: Ayuda (PRF-017)

    private var helpCard: some View {
        ProfileCard(background: MarginColors.ink) {
            Text("Preguntas frecuentes")
                .textCase(.uppercase)
                .font(.app(11, .semibold))
                .tracking(11 * 0.08)
                .foregroundStyle(MarginColors.yellow)
                .padding(EdgeInsets(top: 16, leading: 16, bottom: 6, trailing: 16))
                .frame(maxWidth: .infinity, alignment: .leading)
            FaqRows(questions: Faq.card, onOpen: actions.openFaq ?? { _ in actions.comingSoon() })
            CardDivider(color: MarginColors.inkLine)
            ProfileRow(minHeight: 52, action: actions.openAllFaq ?? actions.comingSoon) {
                Text("Todas las preguntas").font(.app(15, .semibold)).foregroundStyle(MarginColors.yellow)
                Spacer(minLength: 0)
                ProfileIcon(name: "ArrowForward", color: MarginColors.yellow)
            }
        }
    }

    // MARK: Pie (PRF-019)

    private var footer: some View {
        HStack {
            Text("margin. \(version)")
            Spacer()
            Button("Términos", action: actions.comingSoon).buttonStyle(.plain)
            Text("·")
            Button("Privacidad", action: actions.comingSoon).buttonStyle(.plain)
        }
        .font(.app(11))
        .foregroundStyle(MarginColors.muted)
        .padding(EdgeInsets(top: 24, leading: 24, bottom: 24, trailing: 24))
    }
}
