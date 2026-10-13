import ReaderDomain
import SwiftUI

/// PRF-020: contraseña actual, nueva (con las reglas de ONB-005 y el medidor) y confirmación, con el estilo del
/// onboarding sobre el fondo de Perfil. Atrás descarta sin cambiar nada.
struct ChangePasswordView: View {
    @State private var viewModel: ChangePasswordViewModel
    let onBack: () -> Void
    let onSaved: () -> Void

    init(viewModel: ChangePasswordViewModel, onBack: @escaping () -> Void, onSaved: @escaping () -> Void) {
        _viewModel = State(initialValue: viewModel)
        self.onBack = onBack
        self.onSaved = onSaved
    }

    var body: some View {
        @Bindable var vm = viewModel
        OnboardingPage(background: MarginColors.paper) {
            Button(action: onBack) {
                ProfileIcon(name: "ArrowBack", size: 24)
                    .frame(width: 44, height: 44)
                    .contentShape(Circle())
            }
            .buttonStyle(.plain)
            .disabled(vm.busy)
            .padding(.leading, 12)
            .padding(.top, 8)
            .accessibilityLabel("Volver")
            Text("Cambiar contraseña")
                .font(.app(22, .bold))
                .tracking(-22 * 0.02)
                .foregroundStyle(MarginColors.ink)
                .padding(EdgeInsets(top: 12, leading: 24, bottom: 8, trailing: 24))
                .accessibilityAddTraits(.isHeader)
            VStack(alignment: .leading, spacing: 12) {
                OnbTextField(
                    label: "Contraseña actual", text: $vm.current, kind: .password, error: vm.currentError,
                    visible: $vm.currentVisible
                )
                OnbTextField(
                    label: "Contraseña nueva", text: $vm.password, kind: .newPassword, error: vm.newPasswordError,
                    visible: $vm.passwordVisible
                )
                let check = vm.passwordCheck
                VStack(alignment: .leading, spacing: 8) {
                    StrengthMeter(check: check).foregroundStyle(MarginColors.ink)
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
            .padding(.top, 12)
        } bottom: {
            PrimaryButton(text: "Guardar", enabled: vm.canSave, busy: vm.busy, trailing: nil) {
                Task { await vm.save() }
            }
            .padding(.horizontal, 20)
            .padding(.bottom, 14)
        }
        .onChange(of: vm.saved) { _, saved in
            if saved { onSaved() }
        }
    }
}
