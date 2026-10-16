import Observation
import ReaderDomain
import SwiftUI

/// Pantallas del onboarding (O1 a O9).
enum OnboardingRoute: Hashable {
    case welcome
    /// L0 (GST-019).
    case guestReady
    /// B3, el final del alta corta (GST-021).
    case shortAllSet
    case signUp(name: String, email: String)
    case verify(justSent: Bool)
    case goal
    case allSet
    case signIn
    case forgot(email: String)
    case reset(code: String)
}

/// Pila del onboarding, como el `NavHost` de Android: `startOver` vacía la pila para que atrás no vuelva a un paso ya
/// cumplido (ONB-013, ONB-016).
@MainActor
@Observable
final class OnboardingRouter {
    var root: OnboardingRoute
    var path: [OnboardingRoute] = []
    /// GST-011, GST-022: alta o ingreso abiertos desde adentro de la app (Perfil, el aviso o el banner). La cruz o
    /// atrás en el primer paso vuelven a Inicio; el final es B3 o la Biblioteca.
    var inApp = false
    /// Cómo volver a Inicio sin terminar (la cruz del alta corta).
    @ObservationIgnored var onExitInApp: () -> Void = {}

    init(root: OnboardingRoute = .welcome) {
        self.root = root
    }

    func push(_ route: OnboardingRoute) { path.append(route) }

    /// Atrás; si no hay nada detrás (se entró por un enlace), a la bienvenida. Desde adentro de la app, a Inicio.
    func pop() {
        if path.isEmpty, inApp {
            onExitInApp()
        } else if path.isEmpty {
            startOver(.welcome)
        } else {
            path.removeLast()
        }
    }

    /// Cambia la pantalla de arriba por otra (por ejemplo crear cuenta → verificar).
    func replaceTop(with route: OnboardingRoute) {
        if path.isEmpty {
            root = route
        } else {
            path[path.count - 1] = route
        }
    }

    /// Empieza de nuevo en `route`, con `then` encima.
    func startOver(_ route: OnboardingRoute, then: [OnboardingRoute] = []) {
        root = route
        path = then
    }
}

/// Lo que necesitan las pantallas del onboarding, armado en `AppGraph`.
struct OnboardingDependencies {
    let auth: AuthRepository
    let account: AccountRepository
    let preferences: OnboardingPreferences
    let scheduler: ReminderScheduler
    let google: @MainActor () async -> GoogleSignInResult
}

/// El onboarding completo. `onFinish` lleva a Inicio; con verdadero, además abre el selector de EPUB (ONB-013).
struct OnboardingFlow: View {
    @Bindable var router: OnboardingRouter
    let dependencies: OnboardingDependencies
    let onFinish: (_ importBooks: Bool) -> Void
    /// GST-021, GST-022: con la cuenta lista desde adentro de la app, a la Biblioteca.
    var onFinishInApp: () -> Void = {}
    /// GST-021: cuántos libros hay en el dispositivo, para B3.
    var localBookCount: () async -> Int = { 0 }
    /// GST-002: "Empezar a leer".
    let onStartReading: () -> Void

    var body: some View {
        NavigationStack(path: $router.path) {
            screen(router.root)
                // Al empezar de nuevo, la raíz es otra pantalla con su propio estado.
                .id(router.root)
                .navigationDestination(for: OnboardingRoute.self, destination: screen)
        }
    }

    @ViewBuilder
    private func screen(_ route: OnboardingRoute) -> some View {
        let deps = dependencies
        switch route {
        case .welcome:
            WelcomeView(
                onStartReading: {
                    onStartReading()
                    router.startOver(.guestReady)
                },
                onCreateAccount: { router.push(.signUp(name: "", email: "")) },
                onSignIn: { router.push(.signIn) }
            )
        case .shortAllSet:
            ShortAllSetScreen(account: deps.account, localBookCount: localBookCount, onLibrary: onFinishInApp)
        case .guestReady:
            GuestReadyView(onStartReading: { onFinish(false) }, onImportBooks: { onFinish(true) })
        case let .signUp(name, email):
            SignUpView(
                viewModel: SignUpViewModel(auth: deps.auth, name: name, email: email),
                google: deps.google,
                onBack: router.pop,
                onNext: { next in
                    switch next {
                    // Con la cuenta creada, crear cuenta ya no está detrás.
                    case .verifyEmail: router.replaceTop(with: .verify(justSent: true))
                    // GST-011: el alta corta termina en B3, sin meta diaria.
                    case .goal: router.startOver(router.inApp ? .shortAllSet : .goal)
                    }
                },
                onSignIn: { router.replaceTop(with: .signIn) },
                inApp: router.inApp
            )
        case let .verify(justSent):
            VerifyEmailView(
                viewModel: VerifyEmailViewModel(auth: deps.auth, account: deps.account, justSent: justSent),
                inApp: router.inApp,
                onNext: { next in
                    switch next {
                    case .goal: router.startOver(router.inApp ? .shortAllSet : .goal)
                    // ONB-008: de vuelta a crear cuenta, con la bienvenida detrás (desde adentro, con Inicio detrás).
                    case let .change(name, email):
                        if router.inApp {
                            router.startOver(.signUp(name: name, email: email))
                        } else {
                            router.startOver(.welcome, then: [.signUp(name: name, email: email)])
                        }
                    }
                }
            )
        case .goal:
            GoalView(
                viewModel: GoalViewModel(preferences: deps.preferences, scheduler: deps.scheduler),
                // Con la cuenta ya creada, no hay paso anterior (era intereses, ONB-009 retirado).
                onBack: nil,
                onDone: { router.startOver(.allSet) }
            )
        case .allSet:
            AllSetView(
                viewModel: AllSetViewModel(account: deps.account, preferences: deps.preferences),
                onStartReading: { onFinish(false) },
                onImportBooks: { onFinish(true) }
            )
        case .signIn:
            SignInView(
                viewModel: SignInViewModel(auth: deps.auth),
                google: deps.google,
                onBack: router.pop,
                onNext: { next, sent in
                    switch next {
                    // GST-022: desde adentro de la app, al entrar se abre la Biblioteca.
                    case .home: if router.inApp { onFinishInApp() } else { onFinish(false) }
                    case .verifyEmail: router.startOver(.verify(justSent: sent))
                    }
                },
                onForgot: { email in router.push(.forgot(email: email)) },
                onCreateAccount: { router.replaceTop(with: .signUp(name: "", email: "")) }
            )
        case let .forgot(email):
            ForgotPasswordView(viewModel: ForgotPasswordViewModel(auth: deps.auth, email: email), onBack: router.pop)
        case let .reset(code):
            ResetPasswordView(
                viewModel: ResetPasswordViewModel(auth: deps.auth, code: code),
                onClose: router.pop,
                onNext: { next in
                    switch next {
                    case .home: onFinish(false)
                    case .signIn: router.startOver(.welcome, then: [.signIn])
                    }
                },
                onRequestLink: { email in router.startOver(.welcome, then: [.signIn, .forgot(email: email)]) }
            )
        }
    }
}

/// B3 con sus datos: el nombre de la cuenta y los libros del dispositivo (GST-021).
private struct ShortAllSetScreen: View {
    let account: AccountRepository
    let localBookCount: () async -> Int
    let onLibrary: () -> Void
    @State private var name: String?
    @State private var count = 0

    var body: some View {
        ShortAllSetView(name: name, bookCount: count, onLibrary: onLibrary)
            .task {
                name = firstName(await account.currentUser()?.displayName)
                count = await localBookCount()
            }
    }
}
