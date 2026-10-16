import ReaderDomain
import SwiftUI

/// Raíz de la app: el onboarding o Inicio según la sesión (ONB-019), y los enlaces que llegan de afuera: la vuelta de
/// Google, los enlaces de los emails de la cuenta (ONB-007, ONB-018) y los EPUB de "Abrir con" (LIB-001).
struct AppRootView: View {
    let screens: AppGraph.Screens
    @State private var router = OnboardingRouter()
    /// O9 abierto desde un enlace con la app ya en Inicio.
    @State private var resetFromHome: ResetLink?
    @State private var notice: String?

    private var start: AppStartViewModel { screens.start }

    var body: some View {
        content
            .task {
                await start.load()
                if start.start == .verifyEmail { router.startOver(.verify(justSent: false)) }
            }
            .onOpenURL(perform: open)
            #if os(iOS)
            // PRF-021: la apariencia elegida (nil sigue al dispositivo), salvo en el onboarding, que va siempre claro
            // con su barra de estado oscura. El lector elige la suya según su tema.
            .preferredColorScheme(start.start == .home ? screens.appearance.colorScheme : .light)
            #endif
            .overlay(alignment: .bottom) {
                if let notice {
                    Text(notice)
                        .font(.app(14, .medium))
                        .foregroundStyle(.white)
                        .padding(.horizontal, 16)
                        .padding(.vertical, 10)
                        .background(MarginColors.inkFill, in: RoundedRectangle(cornerRadius: 14))
                        .padding(.bottom, 24)
                        .task {
                            AccessibilityNotification.Announcement(notice).post()
                            try? await Task.sleep(for: .seconds(3))
                            self.notice = nil
                        }
                }
            }
    }

    @ViewBuilder
    private var content: some View {
        switch start.start {
        case .loading:
            // ONB-019: mientras no se sabe la sesión, ni bienvenida ni Inicio.
            MarginColors.paper.ignoresSafeArea()
        case .welcome, .verifyEmail:
            if let onboarding = screens.onboarding {
                OnboardingFlow(
                    router: router,
                    dependencies: onboarding,
                    onFinish: { importBooks in
                        router.inApp = false
                        start.enterHome(importBooks: importBooks)
                    },
                    onFinishInApp: {
                        router.inApp = false
                        start.enterHome(importBooks: false, openLibrary: true)
                    },
                    localBookCount: { await screens.library.bookCount() },
                    onStartReading: { start.markWelcomeSeen() }
                )
                // PRF-021: el onboarding se ve siempre como su diseño (O1 a O9).
                .environment(\.colorScheme, .light)
            }
        case .home:
            RootView(
                home: screens.home, search: screens.search, library: screens.library, sync: screens.sync, profile: screens.profile,
                bookActions: screens.bookActions, makeReader: screens.makeReader, makeDetail: screens.makeDetail, plans: screens.plans,
                importOnAppear: start.pendingImport, onImportOpened: start.importOpened,
                libraryOnAppear: start.pendingLibrary, onLibraryOpened: start.libraryOpened,
                onSignedOut: { backToOnboarding(.welcome, then: []) },
                // GST-011, GST-022: el alta corta y el ingreso salen de Inicio y vuelven a entrar, así RootView se arma de
                // nuevo y la primera pasada con la sesión sube lo que había en el dispositivo (GST-012).
                onCreateAccount: { backToOnboarding(.signUp(name: "", email: ""), then: [], inApp: true) },
                onSignIn: { backToOnboarding(.signIn, then: [], inApp: true) }
            )
            .sheet(item: $resetFromHome) { link in
                if let onboarding = screens.onboarding {
                    ResetPasswordView(
                        viewModel: ResetPasswordViewModel(auth: onboarding.auth, code: link.code),
                        onClose: { resetFromHome = nil },
                        onNext: { next in
                            resetFromHome = nil
                            if next == .signIn { backToOnboarding(.welcome, then: [.signIn]) }
                        },
                        onRequestLink: { email in
                            resetFromHome = nil
                            backToOnboarding(.welcome, then: [.signIn, .forgot(email: email)])
                        }
                    )
                    .environment(\.colorScheme, .light)
                }
            }
        }
    }

    private func open(_ url: URL) {
        if GoogleSignInClient.handle(url) { return }
        if let host = screens.authLinkHost, let link = parseAuthLink(url, host: host), let onboarding = screens.onboarding {
            switch link {
            case let .resetPassword(code):
                if start.start == .home {
                    resetFromHome = ResetLink(code: code)
                } else {
                    router.push(.reset(code: code))
                }
            case let .verifyEmail(code):
                // ONB-007: la pantalla de verificación, si está a la vista, avanza sola.
                Task {
                    let applied = (try? await onboarding.auth.applyEmailVerification(code: code)) != nil
                    notice = applied ? "Email verificado." : AuthError.invalidLink.message
                }
            }
            return
        }
        // "Abrir con" desde otra app, o un EPUB tocado en Archivos (LIB-001).
        Task { await screens.home.openIncoming(url) }
    }

    private func backToOnboarding(_ route: OnboardingRoute, then: [OnboardingRoute], inApp: Bool = false) {
        router.inApp = inApp
        // GST-011: la cruz del alta corta vuelve a Inicio, donde estaba.
        router.onExitInApp = {
            router.inApp = false
            start.enterHome(importBooks: false)
        }
        router.startOver(route, then: then)
        start.leaveHome()
    }
}

private struct ResetLink: Identifiable {
    let code: String
    var id: String { code }
}
