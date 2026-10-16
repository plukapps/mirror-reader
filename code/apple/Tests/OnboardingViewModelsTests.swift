import Foundation
import ReaderDomain
import Testing
@testable import Reader

// Mismos casos que `OnboardingViewModelsTest` y `StartTest` de Android (ONB-004 a ONB-021). Las esperas (60 s, 30 s,
// consulta cada 3 s) se acortan por inicializador.

/// Cuenta falsa: guarda lo que se le pidió y responde lo que el test configura.
@MainActor
private final class FakeAuth: AccountRepository, AuthRepository, @unchecked Sendable {
    var user: AccountUser?
    var createResult: Result<AccountUser, AuthFailure> = .success(
        AccountUser(id: "u1", email: "ana@mail.com", displayName: "Ana Ruiz", needsEmailVerification: true)
    )
    var signInResult: Result<AccountUser, AuthFailure> = .success(AccountUser(id: "u1", email: "ana@mail.com"))
    var googleResult: Result<AccountUser, AuthFailure> = .success(AccountUser(id: "u1", email: "ana@gmail.com", displayName: "Ana"))
    var verified: Result<Bool, AuthFailure> = .success(false)
    var sendVerificationResult: Result<Void, AuthFailure> = .success(())
    var discardResult: Result<Void, AuthFailure> = .success(())
    var resetResult: Result<Void, AuthFailure> = .success(())
    var resetEmail: Result<String, AuthFailure> = .success("ana@mail.com")
    var confirmResult: Result<Void, AuthFailure> = .success(())

    /// Demora de `createAccount`, para ver el estado mientras espera al servidor.
    var createDelay: Duration = .zero
    private(set) var created: [[String]] = []
    private(set) var signIns: [[String]] = []
    private(set) var googleSignIns: [GoogleTokens] = []
    private(set) var verificationChecks = 0
    private(set) var verificationsSent = 0
    private(set) var discarded = 0
    private(set) var resetsSent: [String] = []
    private(set) var confirmed: [[String]] = []

    nonisolated init() {}

    func currentUser() async -> AccountUser? { user }

    func createAccount(name: String, email: String, password: String) async throws -> AccountUser {
        created.append([name, email, password])
        try? await Task.sleep(for: createDelay)
        return try createResult.get()
    }

    func signInWithPassword(email: String, password: String) async throws -> AccountUser {
        signIns.append([email, password])
        return try signInResult.get()
    }

    func signInWithGoogle(_ tokens: GoogleTokens) async throws -> AccountUser {
        googleSignIns.append(tokens)
        return try googleResult.get()
    }

    func sendEmailVerification() async throws {
        verificationsSent += 1
        try sendVerificationResult.get()
    }

    func refreshEmailVerified() async throws -> Bool {
        verificationChecks += 1
        return try verified.get()
    }

    func discardUnverifiedAccount() async throws {
        discarded += 1
        try discardResult.get()
    }

    func sendPasswordReset(email: String) async throws {
        resetsSent.append(email)
        try resetResult.get()
    }

    func emailForResetCode(_ code: String) async throws -> String { try resetEmail.get() }

    func confirmPasswordReset(code: String, newPassword: String) async throws {
        confirmed.append([code, newPassword])
        try confirmResult.get()
    }

    func applyEmailVerification(code: String) async throws {}
}

private final class FakePreferences: OnboardingPreferences, @unchecked Sendable {
    var storedGoal: ReadingGoal?
    var storedReminder: DailyReminder?

    func goal() -> ReadingGoal? { storedGoal }
    func reminder() -> DailyReminder? { storedReminder }

    func saveGoal(_ goal: ReadingGoal, reminder: DailyReminder) {
        storedGoal = goal
        storedReminder = reminder
    }
}

private actor FakeScheduler: ReminderScheduler {
    let allowed: Bool
    private(set) var scheduled: (DailyReminder, ReadingGoal?)?
    private(set) var cancelled = 0
    private(set) var permissionRequests = 0

    init(allowed: Bool = true) { self.allowed = allowed }

    func requestPermission() async -> Bool {
        permissionRequests += 1
        return allowed
    }

    func schedule(_ reminder: DailyReminder, goal: ReadingGoal?) async { scheduled = (reminder, goal) }

    func cancel() async {
        scheduled = nil
        cancelled += 1
    }
}

private let tokens = GoogleTokens(idToken: "id", accessToken: "access")

/// Espera hasta que se cumpla la condición (o 2 s), dejando correr las tareas de los ViewModels.
@MainActor
private func eventually(_ condition: () -> Bool) async {
    let deadline = ContinuousClock.now + .seconds(2)
    while !condition(), ContinuousClock.now < deadline {
        try? await Task.sleep(for: .milliseconds(5))
    }
}

@MainActor
struct SignUpViewModelTests {
    private let auth = FakeAuth()

    private func filled() -> SignUpViewModel {
        let vm = SignUpViewModel(auth: auth)
        vm.name = "Ana Ruiz"
        vm.email = "ana@mail.com"
        vm.password = "slow-reads-2026"
        vm.termsAccepted = true
        return vm
    }

    @Test("ONB-004: nombre, email válido, contraseña válida y términos habilitan el botón")
    func everyConditionEnables() {
        let vm = filled()
        #expect(vm.canSubmit)

        vm.termsAccepted = false
        #expect(!vm.canSubmit)
        vm.termsAccepted = true
        vm.password = "abcdefg1"
        #expect(!vm.canSubmit)
        vm.password = "slow-reads-2026"
        vm.email = "ana@mail"
        #expect(!vm.canSubmit)
        vm.email = "ana@mail.com"
        vm.name = " "
        #expect(!vm.canSubmit)
    }

    @Test("ONB-006, ONB-021: crear la cuenta lleva a verificar y olvida la contraseña")
    func createGoesToVerify() async {
        auth.createDelay = .milliseconds(100)
        let vm = filled()
        let submitting = Task { await vm.submit() }
        await eventually { vm.busy }
        #expect(vm.busy)
        #expect(!vm.canSubmit)
        await submitting.value

        #expect(auth.created == [["Ana Ruiz", "ana@mail.com", "slow-reads-2026"]])
        #expect(vm.next == .verifyEmail)
        #expect(vm.password.isEmpty)
        #expect(!vm.busy)
    }

    @Test("ONB-006: si el email ya tiene cuenta, se avisa")
    func emailInUse() async {
        auth.createResult = .failure(AuthFailure(.emailInUse))
        let vm = filled()
        await vm.submit()

        #expect(vm.error == .emailInUse)
        #expect(vm.next == nil)
        #expect(!vm.busy)
    }

    @Test("ONB-006: Google salta la verificación")
    func googleGoesToTheGoal() async {
        let vm = SignUpViewModel(auth: auth)
        await vm.google(.tokens(tokens))
        #expect(auth.googleSignIns == [tokens])
        #expect(vm.next == .goal)
    }

    @Test("ONB-020: cancelar Google no muestra error; un fallo, sí")
    func googleCancelAndFailure() async {
        let vm = SignUpViewModel(auth: auth)
        await vm.google(.cancelled)
        #expect(vm.error == nil)
        await vm.google(.failed)
        #expect(vm.error == .unknown)
        #expect(auth.googleSignIns.isEmpty)
    }

    @Test("ONB-008: los datos de \"Cambiar\" vuelven sin la contraseña")
    func startsWithDataBroughtBack() {
        let vm = SignUpViewModel(auth: auth, name: "Ana", email: "ana@mail.com")
        #expect(vm.name == "Ana")
        #expect(vm.email == "ana@mail.com")
        #expect(vm.password.isEmpty)
    }
}

@MainActor
struct VerifyEmailViewModelTests {
    private let auth = FakeAuth()

    private func viewModel(justSent: Bool = false) -> VerifyEmailViewModel {
        VerifyEmailViewModel(auth: auth, account: auth, justSent: justSent, pollInterval: .milliseconds(30), second: .milliseconds(10))
    }

    @Test("ONB-007: detecta la verificación sola mientras se ve")
    func detectsVerificationWhileVisible() async {
        let vm = viewModel()
        vm.onVisible()
        await eventually { auth.verificationChecks >= 1 }
        #expect(vm.next == nil)

        auth.verified = .success(true)
        await eventually { vm.next != nil }
        #expect(vm.next == .goal)
        vm.onHidden()
    }

    @Test("ONB-007: oculta, no consulta")
    func hiddenStopsPolling() async {
        let vm = viewModel()
        vm.onVisible()
        await eventually { auth.verificationChecks >= 1 }
        vm.onHidden()
        let checks = auth.verificationChecks
        try? await Task.sleep(for: .milliseconds(150))
        #expect(auth.verificationChecks == checks)
    }

    @Test("ONB-007: reenviar espera 60 s desde que salió el email")
    func resendWaits() async {
        auth.user = AccountUser(id: "u1", email: "ana@mail.com", displayName: "Ana", needsEmailVerification: true)
        let vm = viewModel(justSent: true)
        await vm.load()
        #expect(vm.email == "ana@mail.com")
        #expect(vm.resendSeconds == 60)
        #expect(!vm.canResend)

        await vm.resend()
        #expect(auth.verificationsSent == 0)

        await eventually { vm.canResend }
        #expect(vm.canResend)
        await vm.resend()
        #expect(auth.verificationsSent == 1)
        #expect(vm.resent)
        #expect(vm.resendSeconds == 60)
    }

    @Test("ONB-008: \"Cambiar\" descarta la cuenta sin verificar y devuelve los datos")
    func changeDiscards() async {
        auth.user = AccountUser(id: "u1", email: "ana@mail.com", displayName: "Ana Ruiz", needsEmailVerification: true)
        let vm = viewModel()
        await vm.load()
        await vm.change()

        #expect(auth.discarded == 1)
        #expect(vm.next == .change(name: "Ana Ruiz", email: "ana@mail.com"))
    }

    @Test("ONB-008: con la cuenta ya verificada, sigue sin borrarla")
    func changeOnVerifiedGoesOn() async {
        auth.verified = .success(true)
        let vm = viewModel()
        await vm.change()

        #expect(auth.discarded == 0)
        #expect(vm.next == .goal)
    }
}

@MainActor
struct OnboardingStepsTests {


    @Test("ONB-010, ONB-012: terminar guarda la meta y programa el recordatorio")
    func finishSchedules() async {
        let prefs = FakePreferences()
        let scheduler = FakeScheduler()
        let vm = GoalViewModel(preferences: prefs, scheduler: scheduler)
        #expect(vm.goal == .regular)
        #expect(vm.reminder == DailyReminder(enabled: true, hour: 21, minute: 30))

        vm.goal = .serious
        vm.setReminderTime(hour: 22, minute: 0)
        await vm.finish()

        let expected = DailyReminder(enabled: true, hour: 22, minute: 0)
        #expect(prefs.storedGoal == .serious)
        #expect(prefs.storedReminder == expected)
        let scheduled = await scheduler.scheduled
        #expect(scheduled?.0 == expected)
        #expect(scheduled?.1 == .serious)
        #expect(vm.done)
        #expect(!vm.notificationsDenied)
    }

    @Test("ONB-011: sin permiso se guarda con el recordatorio apagado")
    func withoutPermission() async {
        let prefs = FakePreferences()
        let scheduler = FakeScheduler(allowed: false)
        let vm = GoalViewModel(preferences: prefs, scheduler: scheduler)
        await vm.finish()

        #expect(prefs.storedReminder == DailyReminder(enabled: false, hour: 21, minute: 30))
        #expect(await scheduler.scheduled == nil)
        #expect(await scheduler.cancelled == 1)
        #expect(vm.notificationsDenied)
        #expect(vm.done)
    }

    @Test("ONB-011: con el recordatorio apagado no se pide el permiso")
    func reminderOffAsksNothing() async {
        let scheduler = FakeScheduler()
        let vm = GoalViewModel(preferences: FakePreferences(), scheduler: scheduler)
        vm.reminderEnabled = false
        await vm.finish()
        #expect(await scheduler.permissionRequests == 0)
        #expect(await scheduler.cancelled == 1)
    }

    @Test("ONB-010: omitir la meta no guarda nada")
    func skippingGoal() async {
        let prefs = FakePreferences()
        let scheduler = FakeScheduler()
        let vm = GoalViewModel(preferences: prefs, scheduler: scheduler)
        vm.skip()
        #expect(vm.done)
        #expect(prefs.storedGoal == nil)
        #expect(await scheduler.scheduled == nil)
    }

    @Test("ONB-013: saluda por el primer nombre con la meta elegida")
    func allSetGreets() async {
        let prefs = FakePreferences()
        prefs.saveGoal(.regular, reminder: .default)
        let auth = FakeAuth()
        auth.user = AccountUser(id: "u1", email: "ana@mail.com", displayName: "Ana Ruiz")
        let vm = AllSetViewModel(account: auth, preferences: prefs)
        await vm.load()

        #expect(vm.name == "Ana")
        #expect(vm.goal == .regular)
        #expect(vm.reminder == .default)
    }
}

@MainActor
struct SignInViewModelTests {
    private let auth = FakeAuth()

    private func filled() -> SignInViewModel {
        let vm = SignInViewModel(auth: auth)
        vm.email = "ana@mail.com"
        vm.password = "x"
        return vm
    }

    @Test("ONB-016: ingresar va a Inicio, o a verificar si falta")
    func goesHomeOrToVerification() async {
        let vm = filled()
        await vm.submit()
        #expect(vm.next == .home)

        auth.signInResult = .success(AccountUser(id: "u1", email: "ana@mail.com", needsEmailVerification: true))
        vm.onNavigated()
        vm.password = "x"
        await vm.submit()
        #expect(vm.next == .verifyEmail)
    }

    @Test("ONB-016: al ingresar sin verificar, el email sale en ese momento")
    func unverifiedSendsEmail() async {
        auth.signInResult = .success(AccountUser(id: "u1", email: "ana@mail.com", needsEmailVerification: true))
        let vm = filled()
        await vm.submit()

        #expect(auth.verificationsSent == 1)
        #expect(vm.verificationSent)
        #expect(vm.next == .verifyEmail)
    }

    @Test("ONB-016: una cuenta verificada no recibe email")
    func verifiedSendsNothing() async {
        let vm = filled()
        await vm.submit()
        #expect(auth.verificationsSent == 0)
    }

    @Test("ONB-015: el error se muestra hasta que se escribe")
    func errorUntilTyping() async {
        auth.signInResult = .failure(AuthFailure(.invalidCredentials))
        let vm = filled()
        await vm.submit()
        #expect(vm.error == .invalidCredentials)
        #expect(vm.next == nil)

        vm.password = "x2"
        #expect(vm.error == nil)
    }

    @Test("ONB-020: un fallo de Google se muestra")
    func googleFailure() async {
        let vm = SignInViewModel(auth: auth)
        await vm.google(.failed)
        #expect(vm.error == .unknown)
    }
}

@MainActor
struct PasswordRecoveryTests {
    private let auth = FakeAuth()

    @Test("ONB-017: envía el enlace y espera 30 s para reenviar")
    func forgotSendsAndWaits() async {
        let vm = ForgotPasswordViewModel(auth: auth, email: "ana@mail.com", second: .milliseconds(10))
        #expect(vm.canSend)
        await vm.send()

        #expect(auth.resetsSent == ["ana@mail.com"])
        #expect(vm.sent)
        #expect(vm.resendSeconds == 30)
        #expect(!vm.canSend)

        await eventually { vm.canSend }
        #expect(vm.canSend)
    }

    @Test("ONB-018: la contraseña nueva tiene que ser válida e igual en los dos campos, y entra")
    func newPasswordSignsIn() async {
        let vm = ResetPasswordViewModel(auth: auth, code: "code")
        await vm.load()
        #expect(vm.email == "ana@mail.com")

        vm.password = "slow-reads-2026"
        vm.confirm = "slow-reads-202"
        #expect(vm.mismatch)
        #expect(!vm.canSave)

        vm.confirm = "slow-reads-2026"
        #expect(vm.canSave)
        await vm.save()

        #expect(auth.confirmed == [["code", "slow-reads-2026"]])
        #expect(auth.signIns == [["ana@mail.com", "slow-reads-2026"]])
        #expect(vm.next == .home)
    }

    @Test("ONB-018: un enlace vencido o usado se avisa")
    func expiredLink() async {
        auth.resetEmail = .failure(AuthFailure(.invalidLink))
        let vm = ResetPasswordViewModel(auth: auth, code: "old")
        await vm.load()
        #expect(vm.linkInvalid)
        #expect(!vm.loading)
    }
}

/// GST-002: bienvenida vista, en memoria.
final class MemoryWelcome: WelcomePreference, @unchecked Sendable {
    private(set) var marked = false
    init(seen: Bool = false) { marked = seen }
    func seen() -> Bool { marked }
    func markSeen() { marked = true }
}

@MainActor
struct AppStartTests {
    @Test("GST-003: sin sesión, quien ya pasó por la bienvenida entra a Inicio")
    func withoutSessionAfterTheWelcome() async {
        let vm = AppStartViewModel(account: FakeAuth(), welcome: MemoryWelcome(seen: true))
        await vm.load()
        #expect(vm.start == .home)
    }

    @Test("GST-004: con sesión la bienvenida no cuenta")
    func sessionIgnoresTheWelcome() async {
        let auth = FakeAuth()
        auth.user = AccountUser(id: "u1", email: "a@b.c")
        let vm = AppStartViewModel(account: auth, welcome: MemoryWelcome(seen: false))
        await vm.load()
        #expect(vm.start == .home)
    }

    @Test("GST-002: Empezar a leer registra la bienvenida")
    func startReadingMarksTheWelcome() {
        let welcome = MemoryWelcome()
        AppStartViewModel(account: FakeAuth(), welcome: welcome).markWelcomeSeen()
        #expect(welcome.seen())
    }

    @Test("GST-003: sin sesión abre la bienvenida")
    func withoutSession() async {
        let vm = AppStartViewModel(account: FakeAuth(), welcome: MemoryWelcome())
        #expect(vm.start == .loading)
        await vm.load()
        #expect(vm.start == .welcome)
    }

    @Test("ONB-019: una cuenta de email sin verificar abre la verificación")
    func unverified() async {
        let auth = FakeAuth()
        auth.user = AccountUser(id: "u1", email: "a@b.c", needsEmailVerification: true)
        let vm = AppStartViewModel(account: auth, welcome: MemoryWelcome())
        await vm.load()
        #expect(vm.start == .verifyEmail)
    }

    @Test("ONB-019: con sesión verificada abre Inicio")
    func verified() async {
        let auth = FakeAuth()
        auth.user = AccountUser(id: "u1", email: "a@b.c")
        let vm = AppStartViewModel(account: auth, welcome: MemoryWelcome())
        await vm.load()
        #expect(vm.start == .home)
    }

    @Test("ONB-019: se decide una vez por arranque")
    func decidedOnce() async {
        let auth = FakeAuth()
        auth.user = AccountUser(id: "u1", email: "a@b.c")
        let vm = AppStartViewModel(account: auth, welcome: MemoryWelcome())
        await vm.load()
        auth.user = nil
        await vm.load()
        #expect(vm.start == .home)
    }

    @Test("ADR 0015: sin Firebase la app abre Inicio y funciona en local")
    func localOnly() async {
        let vm = AppStartViewModel(account: nil, welcome: MemoryWelcome())
        await vm.load()
        #expect(vm.start == .home)
    }
}
