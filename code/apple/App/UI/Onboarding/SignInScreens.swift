import ReaderDomain
import SwiftUI

/// Iniciar sesión, O7 (ONB-014 a ONB-016).
struct SignInView: View {
    @State private var viewModel: SignInViewModel
    let google: () async -> GoogleSignInResult
    let onBack: () -> Void
    /// Adónde ir y, si es a verificar, si el email acaba de salir (ONB-016).
    let onNext: (SignInViewModel.Next, _ verificationSent: Bool) -> Void
    let onForgot: (_ email: String) -> Void
    let onCreateAccount: () -> Void

    init(
        viewModel: SignInViewModel,
        google: @escaping () async -> GoogleSignInResult,
        onBack: @escaping () -> Void,
        onNext: @escaping (SignInViewModel.Next, Bool) -> Void,
        onForgot: @escaping (String) -> Void,
        onCreateAccount: @escaping () -> Void
    ) {
        _viewModel = State(initialValue: viewModel)
        self.google = google
        self.onBack = onBack
        self.onNext = onNext
        self.onForgot = onForgot
        self.onCreateAccount = onCreateAccount
    }

    var body: some View {
        @Bindable var vm = viewModel
        OnboardingPage(background: MarginColors.paper) {
            StepHeader(onBack: onBack)
            OnbTitle(text: "Te damos la\nbienvenida.", size: 44).padding(.top, 20)
            VStack(alignment: .leading, spacing: 12) {
                OnbTextField(label: "Email", text: $vm.email, kind: .email)
                OnbTextField(
                    label: "Contraseña", text: $vm.password, kind: .password,
                    // ONB-015: el error va bajo la contraseña.
                    error: vm.error?.message, visible: $vm.passwordVisible,
                    submitLabel: .go, onSubmit: { Task { await vm.submit() } }
                )
                HStack {
                    Spacer()
                    TextLink(text: "¿Olvidaste tu contraseña?", size: 13) { onForgot(vm.email) }
                }
            }
            .padding(.horizontal, 20)
            .padding(.top, 24)
            PrimaryButton(text: "Iniciar sesión", enabled: vm.canSubmit, busy: vm.busy, trailing: nil) {
                Task { await vm.submit() }
            }
            .padding(.horizontal, 20)
            .padding(.top, 12)
            OrDivider(text: "o").padding(.top, 18)
            GoogleButton(enabled: !vm.busy) { Task { await vm.google(await google()) } }
                .padding(.horizontal, 20)
                .padding(.top, 16)
        } bottom: {
            HStack(spacing: 0) {
                Text("¿Nuevo en margin.?").font(.app(13)).foregroundStyle(OnbColors.soft)
                TextLink(text: "Crear cuenta", size: 13, action: onCreateAccount)
            }
            .frame(maxWidth: .infinity)
            .padding(.horizontal, 20)
            .padding(.bottom, 8)
        }
        .onChange(of: vm.next) { _, next in
            guard let next else { return }
            vm.onNavigated()
            onNext(next, vm.verificationSent)
        }
    }
}

/// Olvidé mi contraseña, O8 (ONB-017).
struct ForgotPasswordView: View {
    @State private var viewModel: ForgotPasswordViewModel
    let onBack: () -> Void
    @Environment(\.openURL) private var openURL

    init(viewModel: ForgotPasswordViewModel, onBack: @escaping () -> Void) {
        _viewModel = State(initialValue: viewModel)
        self.onBack = onBack
    }

    var body: some View {
        @Bindable var vm = viewModel
        OnboardingPage(background: MarginColors.paper) {
            StepHeader(onBack: onBack)
            IconBadge(systemName: "lock.rotation").padding(.top, 16)
            OnbTitle(text: "¿Olvidaste tu\ncontraseña?").padding(.top, 16)
            OnbBody(text: "Escribí tu email y te enviamos un enlace para elegir una nueva.").padding(.top, 10)
            OnbTextField(
                label: "Email", text: $vm.email, kind: .email, error: vm.error?.message,
                submitLabel: .send, onSubmit: { Task { await vm.send() } }
            )
            .padding(.horizontal, 20)
            .padding(.top, 20)
            VStack(spacing: 10) {
                if !vm.sent {
                    PrimaryButton(text: "Enviar enlace", enabled: vm.canSend, busy: vm.busy) { Task { await vm.send() } }
                } else {
                    PrimaryButton(text: "Abrir app de correo", leading: "arrow.up.right.square", trailing: nil) {
                        openEmailApp(openURL)
                    }
                    if vm.resendSeconds > 0 {
                        Text("Reenviar enlace en \(formatSeconds(vm.resendSeconds))")
                            .font(.app(14, .semibold))
                            .foregroundStyle(MarginColors.muted)
                            .padding(.vertical, 14)
                    } else {
                        TextLink(text: "Reenviar enlace") { Task { await vm.send() } }
                    }
                }
            }
            .padding(.horizontal, 20)
            .padding(.top, 16)
        } bottom: {
            if vm.sent {
                ConfirmationToast(title: "Enlace enviado", detail: "¿No llegó? Revisá spam.").padding(.bottom, 12)
            }
        }
    }
}

/// Nueva contraseña desde el enlace del email, O9 (ONB-018).
struct ResetPasswordView: View {
    @State private var viewModel: ResetPasswordViewModel
    let onClose: () -> Void
    let onNext: (ResetPasswordViewModel.Next) -> Void
    let onRequestLink: (_ email: String) -> Void

    init(
        viewModel: ResetPasswordViewModel,
        onClose: @escaping () -> Void,
        onNext: @escaping (ResetPasswordViewModel.Next) -> Void,
        onRequestLink: @escaping (String) -> Void
    ) {
        _viewModel = State(initialValue: viewModel)
        self.onClose = onClose
        self.onNext = onNext
        self.onRequestLink = onRequestLink
    }

    var body: some View {
        @Bindable var vm = viewModel
        OnboardingPage(background: MarginColors.paper) {
            StepHeader(onBack: onClose, backIcon: "xmark", backLabel: "Cerrar")
            if vm.loading {
                ProgressView().tint(MarginColors.ink).frame(maxWidth: .infinity).padding(.top, 80)
            } else if vm.linkInvalid {
                IconBadge(systemName: "lock.rotation").padding(.top, 16)
                OnbTitle(text: "El enlace venció").padding(.top, 16)
                OnbBody(text: "Ya se usó o pasó demasiado tiempo. Pedí otro y abrilo desde este dispositivo.").padding(.top, 10)
                PrimaryButton(text: "Pedir otro enlace") { onRequestLink(vm.email) }
                    .padding(.horizontal, 20)
                    .padding(.top, 24)
            } else {
                OnbTitle(text: "Elegí una\ncontraseña nueva").padding(.top, 20)
                OnbBody(text: "Para \(vm.email)").padding(.top, 10)
                VStack(alignment: .leading, spacing: 12) {
                    OnbTextField(label: "Contraseña nueva", text: $vm.password, kind: .newPassword, visible: $vm.passwordVisible)
                    let check = vm.passwordCheck
                    VStack(alignment: .leading, spacing: 8) {
                        RuleRow(text: "Al menos 8 caracteres", met: check.longEnough)
                        RuleRow(text: "Un número", met: check.hasNumber)
                        RuleRow(text: "Un símbolo", met: check.hasSymbol)
                        RuleRow(text: "Una mayúscula", met: check.hasUppercase)
                        if !vm.password.isEmpty, !check.isValid {
                            Text("Cumplí el largo y dos de las otras tres.").font(.app(12)).foregroundStyle(MarginColors.muted)
                        }
                    }
                    .padding(.vertical, 4)
                    OnbTextField(
                        label: "Confirmar contraseña", text: $vm.confirm, kind: .newPassword, placeholder: "Repetila",
                        error: vm.mismatch ? "Las contraseñas no coinciden." : vm.error?.message,
                        visible: $vm.passwordVisible,
                        submitLabel: .done, onSubmit: { Task { await vm.save() } }
                    )
                }
                .padding(.horizontal, 20)
                .padding(.top, 20)
            }
        } bottom: {
            if !vm.linkInvalid, !vm.loading {
                PrimaryButton(text: "Guardar e ingresar", enabled: vm.canSave, busy: vm.busy, trailing: nil) {
                    Task { await vm.save() }
                }
                .padding(.horizontal, 20)
                .padding(.bottom, 14)
            }
        }
        .task { await vm.load() }
        .onChange(of: vm.next) { _, next in
            guard let next else { return }
            vm.onNavigated()
            onNext(next)
        }
    }
}
