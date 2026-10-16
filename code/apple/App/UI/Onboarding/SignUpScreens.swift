import ReaderDomain
import SwiftUI

/// Bienvenida, diseño O1 v2 (GST-001): "Empezar a leer" sin cuenta y, para sincronizar, "Crear cuenta" e "Iniciar
/// sesión" (GST-002).
struct WelcomeView: View {
    let onStartReading: () -> Void
    let onCreateAccount: () -> Void
    let onSignIn: () -> Void

    var body: some View {
        OnboardingPage(background: MarginColors.yellow) {
            Brand().padding(.horizontal, 28).padding(.top, 28)
            Text("Leé más.\nScrolleá menos.\nPensá despacio.")
                .font(.app(44))
                .tracking(-0.045 * 44)
                .lineLimit(3)
                // "Scrolleá menos." no entra a 44 pt en un teléfono angosto: se achica antes de cortar la línea.
                .minimumScaleFactor(0.6)
                .padding(.horizontal, 24)
                .padding(.top, 64)
                .accessibilityAddTraits(.isHeader)
            Text("Tus EPUB, en un lugar tranquilo, en todos tus dispositivos.")
                .font(.app(15))
                .foregroundStyle(OnbColors.softOnYellow)
                .lineSpacing(5)
                .frame(maxWidth: 270, alignment: .leading)
                .fixedSize(horizontal: false, vertical: true)
                .padding(.horizontal, 28)
                .padding(.top, 20)
        } bottom: {
            VStack(spacing: 6) {
                PrimaryButton(text: "Empezar a leer", action: onStartReading)
                Text("¿Querés sincronizar?")
                    .font(.app(13, .medium))
                    .foregroundStyle(OnbColors.softOnYellow)
                    .padding(.top, 6)
                HStack(spacing: 6) {
                    TextLink(text: "Crear cuenta", action: onCreateAccount)
                    Text("·").font(.app(14, .semibold)).foregroundStyle(OnbColors.softOnYellow).accessibilityHidden(true)
                    TextLink(text: "Iniciar sesión", action: onSignIn)
                }
            }
            .padding(.horizontal, 20)
            TermsText(prefix: "Al continuar aceptás los ", middle: " y la ", suffix: ".")
                .font(.app(11))
                .foregroundStyle(OnbColors.softOnYellow)
                .multilineTextAlignment(.center)
                .frame(maxWidth: .infinity)
                .padding(.horizontal, 28)
                .padding(.top, 4)
                .padding(.bottom, 14)
        }
    }
}

/// "Listo" sin cuenta, diseño L0 (GST-019): las portadas son ilustración, no libros de regalo (la app no trae
/// contenido). Se llega desde "Empezar a leer" de la bienvenida.
struct GuestReadyView: View {
    let onStartReading: () -> Void
    let onImportBooks: () -> Void

    var body: some View {
        OnboardingPage(background: MarginColors.yellow) {
            Spacer().frame(height: 56)
            OnbTitle(text: "Ya podés\nleer.", size: 54, padding: 28)
            Text("Empezá importando tus libros. Todo queda en este dispositivo, sin cuenta.")
                .font(.app(15))
                .foregroundStyle(OnbColors.softOnYellow)
                .lineSpacing(5)
                .frame(maxWidth: 270, alignment: .leading)
                .fixedSize(horizontal: false, vertical: true)
                .padding(.horizontal, 28)
                .padding(.top, 16)
            ReadyCovers().padding(.horizontal, 28).padding(.top, 28)
        } bottom: {
            VStack(spacing: 10) {
                PrimaryButton(text: "Empezar a leer", action: onStartReading)
                SecondaryButton(text: "Importar mis libros", leading: {
                    Image(systemName: "plus").font(.system(size: 17, weight: .semibold))
                }, action: onImportBooks)
            }
            .padding(.horizontal, 20)
            .padding(.bottom, 16)
        }
    }
}

/// Crear cuenta, O2 (ONB-003 a ONB-006).
struct SignUpView: View {
    @State private var viewModel: SignUpViewModel
    let google: () async -> GoogleSignInResult
    let onBack: () -> Void
    let onNext: (SignUpViewModel.Next) -> Void
    let onSignIn: () -> Void

    init(
        viewModel: SignUpViewModel,
        google: @escaping () async -> GoogleSignInResult,
        onBack: @escaping () -> Void,
        onNext: @escaping (SignUpViewModel.Next) -> Void,
        onSignIn: @escaping () -> Void
    ) {
        _viewModel = State(initialValue: viewModel)
        self.google = google
        self.onBack = onBack
        self.onNext = onNext
        self.onSignIn = onSignIn
    }

    var body: some View {
        @Bindable var vm = viewModel
        OnboardingPage(background: MarginColors.paper) {
            StepHeader(onBack: onBack, stepLabel: stepLabel(1))
            StepBar(step: 1)
            OnbTitle(text: "Creá tu\ncuenta").padding(.top, 20)
            GoogleButton(enabled: !vm.busy) { Task { await vm.google(await google()) } }
                .padding(.horizontal, 20)
                .padding(.top, 18)
            OrDivider(text: "o con email").padding(.top, 16)
            VStack(alignment: .leading, spacing: 12) {
                OnbTextField(label: "Nombre", text: $vm.name, kind: .name)
                OnbTextField(
                    label: "Email", text: $vm.email, kind: .email,
                    error: vm.error == .emailInUse ? AuthError.emailInUse.message : nil
                )
                if vm.error == .emailInUse {
                    TextLink(text: "Iniciar sesión", size: 13, action: onSignIn)
                }
                VStack(alignment: .leading, spacing: 6) {
                    OnbTextField(
                        label: "Contraseña", text: $vm.password, kind: .newPassword, visible: $vm.passwordVisible,
                        submitLabel: .done, onSubmit: { Task { await vm.submit() } }
                    )
                    if !vm.password.isEmpty {
                        StrengthMeter(check: vm.passwordCheck)
                        if !vm.passwordCheck.isValid {
                            Text("Al menos 8 caracteres y dos de: un número, un símbolo, una mayúscula.")
                                .font(.app(12))
                                .foregroundStyle(MarginColors.muted)
                                .fixedSize(horizontal: false, vertical: true)
                        }
                    }
                }
            }
            .padding(.horizontal, 20)
            .padding(.top, 14)
            TermsCheckbox(checked: $vm.termsAccepted).padding(.horizontal, 20).padding(.top, 14)
            if let error = vm.error, error != .emailInUse {
                Text(error.message).font(.app(13, .medium)).foregroundStyle(OnbColors.error)
                    .padding(.horizontal, 20).padding(.top, 12)
            }
        } bottom: {
            PrimaryButton(text: "Crear cuenta", enabled: vm.canSubmit, busy: vm.busy) { Task { await vm.submit() } }
                .padding(.horizontal, 20)
                .padding(.bottom, 14)
        }
        .onChange(of: vm.next) { _, next in
            guard let next else { return }
            vm.onNavigated()
            onNext(next)
        }
    }
}

/// Verificar email, O3 (ONB-007, ONB-008).
struct VerifyEmailView: View {
    @State private var viewModel: VerifyEmailViewModel
    let onNext: (VerifyEmailViewModel.Next) -> Void
    @Environment(\.scenePhase) private var scenePhase
    @Environment(\.openURL) private var openURL

    init(viewModel: VerifyEmailViewModel, onNext: @escaping (VerifyEmailViewModel.Next) -> Void) {
        _viewModel = State(initialValue: viewModel)
        self.onNext = onNext
    }

    var body: some View {
        let vm = viewModel
        OnboardingPage(background: MarginColors.paper) {
            StepHeader(onBack: { Task { await vm.change() } }, stepLabel: stepLabel(2))
            StepBar(step: 2)
            IconBadge(systemName: "envelope.badge").padding(.top, 22)
            OnbTitle(text: "Revisá tu bandeja").padding(.top, 16)
            OnbBody(text: "Confirmá \(vm.email) con el enlace que te enviamos por email y volvé acá. Si no lo encontrás, revisá spam o reenvialo.")
                .padding(.top, 10)
            TextLink(text: "Cambiar") { Task { await vm.change() } }.padding(.leading, 14)
            HStack {
                if vm.canResend {
                    IconTextButton(text: "Reenviar email", systemName: "clock") { Task { await vm.resend() } }
                } else {
                    HStack(spacing: 6) {
                        Image(systemName: "clock").font(.system(size: 14))
                        Text("Reenviar en \(formatSeconds(vm.resendSeconds))").font(.app(13, .medium))
                    }
                    .foregroundStyle(MarginColors.muted)
                    .padding(.horizontal, 6)
                }
                Spacer()
                IconTextButton(text: "Abrir email", systemName: "arrow.up.right.square") { openEmailApp(openURL) }
            }
            .padding(.horizontal, 14)
            .padding(.top, 8)
            Text(statusText(vm))
                .font(.app(13, .medium))
                .foregroundStyle(vm.error != nil ? OnbColors.error : MarginColors.muted)
                .padding(.horizontal, 20)
                .padding(.top, 16)
        }
        .task { await vm.load() }
        // ONB-007: consulta mientras se ve y la app está en primer plano.
        .onAppear { if scenePhase == .active { vm.onVisible() } }
        .onDisappear { vm.onHidden() }
        .onChange(of: scenePhase) { _, phase in
            if phase == .active { vm.onVisible() } else { vm.onHidden() }
        }
        .onChange(of: vm.next) { _, next in
            guard let next else { return }
            vm.onNavigated()
            onNext(next)
        }
    }

    private func statusText(_ vm: VerifyEmailViewModel) -> String {
        if let error = vm.error { return error.message }
        return vm.resent ? "Te lo enviamos de nuevo." : "Esperando la confirmación…"
    }
}

/// Fila que salta de línea cuando no entra, como `FlowRow` de Compose.
struct FlowLayout: Layout {
    var spacing: CGFloat

    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        let rows = arrange(width: proposal.width ?? .infinity, subviews: subviews)
        let height = rows.map(\.height).reduce(0, +) + spacing * CGFloat(max(rows.count - 1, 0))
        let width = rows.map(\.width).max() ?? 0
        return CGSize(width: proposal.width ?? width, height: height)
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        var y = bounds.minY
        for row in arrange(width: bounds.width, subviews: subviews) {
            var x = bounds.minX
            for index in row.indices {
                let size = subviews[index].sizeThatFits(.unspecified)
                subviews[index].place(at: CGPoint(x: x, y: y), proposal: ProposedViewSize(size))
                x += size.width + spacing
            }
            y += row.height + spacing
        }
    }

    private struct Row {
        var indices: [Int] = []
        var width: CGFloat = 0
        var height: CGFloat = 0
    }

    private func arrange(width: CGFloat, subviews: Subviews) -> [Row] {
        var rows: [Row] = [Row()]
        for index in subviews.indices {
            let size = subviews[index].sizeThatFits(.unspecified)
            let extra = rows[rows.count - 1].indices.isEmpty ? size.width : rows[rows.count - 1].width + spacing + size.width
            if extra > width, !rows[rows.count - 1].indices.isEmpty {
                rows.append(Row())
            }
            var row = rows[rows.count - 1]
            row.width = row.indices.isEmpty ? size.width : row.width + spacing + size.width
            row.height = max(row.height, size.height)
            row.indices.append(index)
            rows[rows.count - 1] = row
        }
        return rows
    }
}

/// Meta diaria y recordatorio, O5 (ONB-010, ONB-011).
struct GoalView: View {
    @State private var viewModel: GoalViewModel
    let onBack: (() -> Void)?
    let onDone: () -> Void
    @State private var pickingTime = false
    @State private var deniedNotice = false

    init(viewModel: GoalViewModel, onBack: (() -> Void)?, onDone: @escaping () -> Void) {
        _viewModel = State(initialValue: viewModel)
        self.onBack = onBack
        self.onDone = onDone
    }

    var body: some View {
        @Bindable var vm = viewModel
        OnboardingPage(background: MarginColors.paper) {
            StepHeader(onBack: onBack, onSkip: vm.skip)
            StepBar(step: 3)
            Text(stepLabel(3)).font(.app(12, .semibold)).foregroundStyle(MarginColors.muted)
                .padding(.horizontal, 20).padding(.top, 20)
            OnbTitle(text: "¿Cuánto querés\nleer por día?").padding(.top, 8)
            VStack(spacing: 8) {
                ForEach(ReadingGoal.allCases, id: \.self) { goal in
                    GoalOption(goal: goal, selected: goal == vm.goal) { vm.goal = goal }
                }
            }
            .padding(.horizontal, 20)
            .padding(.top, 20)
            ReminderCard(reminder: vm.reminder, enabled: $vm.reminderEnabled, onPickTime: { pickingTime = true })
            .padding(.horizontal, 20)
            .padding(.top, 16)
        } bottom: {
            PrimaryButton(text: "Terminar", busy: vm.busy) { Task { await vm.finish() } }
                .padding(.horizontal, 20)
                .padding(.bottom, 14)
        }
        .sheet(isPresented: $pickingTime) {
            ReminderTimePicker(reminder: vm.reminder) { hour, minute in
                vm.setReminderTime(hour: hour, minute: minute)
            }
        }
        // ONB-011: sin permiso, se avisa antes de seguir.
        .alert("Sin permiso de notificaciones, el recordatorio queda apagado.", isPresented: $deniedNotice) {
            Button("Entendido") {
                vm.onNavigated()
                onDone()
            }
        }
        .onChange(of: vm.done) { _, done in
            guard done else { return }
            if vm.notificationsDenied {
                deniedNotice = true
            } else {
                vm.onNavigated()
                onDone()
            }
        }
    }
}

private struct GoalOption: View {
    let goal: ReadingGoal
    let selected: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 14) {
                Circle()
                    .strokeBorder(selected ? MarginColors.ink : MarginColors.muted, lineWidth: selected ? 6 : 1.5)
                    .background(Circle().fill(MarginColors.field))
                    .frame(width: 20, height: 20)
                Text(goal == .devoted ? "1 hora" : "\(goal.minutes) min").font(.app(16, .bold))
                Spacer()
                Text(goal.label)
                    .font(.app(13, selected ? .semibold : .medium))
                    .foregroundStyle(selected ? MarginColors.ink : MarginColors.muted)
            }
            .padding(.horizontal, 16)
            .frame(height: 60)
            .background(selected ? MarginColors.yellow : MarginColors.field, in: RoundedRectangle(cornerRadius: 18))
            .overlay(
                RoundedRectangle(cornerRadius: 18)
                    .strokeBorder(selected ? MarginColors.ink : OnbColors.line, lineWidth: selected ? 2 : 1.5)
            )
            .contentShape(RoundedRectangle(cornerRadius: 18))
        }
        .buttonStyle(.plain)
        .accessibilityElement(children: .combine)
        .accessibilityAddTraits(selected ? [.isButton, .isSelected] : .isButton)
    }
}

extension ReadingGoal {
    var label: String {
        switch self {
        case .casual: "Tranquilo"
        case .regular: "Regular · ~12 libros/año"
        case .serious: "En serio"
        case .devoted: "Devoto"
        }
    }
}

private struct ReminderCard: View {
    let reminder: DailyReminder
    @Binding var enabled: Bool
    let onPickTime: () -> Void

    var body: some View {
        let time = formatTime(hour: reminder.hour, minute: reminder.minute)
        HStack(spacing: 12) {
            Image(systemName: "bell.fill").font(.system(size: 18)).foregroundStyle(MarginColors.yellow)
                .accessibilityHidden(true)
            VStack(alignment: .leading, spacing: 2) {
                Text("Recordatorio diario").font(.app(14, .semibold)).foregroundStyle(MarginColors.field)
                HStack(spacing: 0) {
                    Text("Todos los días a las ").font(.app(12)).foregroundStyle(OnbColors.caption)
                    Button(action: onPickTime) {
                        Text(time).font(.app(12, .semibold)).underline().foregroundStyle(MarginColors.field)
                            .padding(.vertical, 6).padding(.horizontal, 2)
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel("Cambiar la hora del recordatorio, \(time)")
                }
            }
            Spacer()
            Toggle("Recordatorio diario", isOn: $enabled)
                .labelsHidden()
                .tint(MarginColors.yellow)
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 14)
        .background(MarginColors.ink, in: RoundedRectangle(cornerRadius: 18))
    }
}

/// Elegir la hora del recordatorio, como el diálogo de Android.
private struct ReminderTimePicker: View {
    let onConfirm: (Int, Int) -> Void
    @State private var time: Date
    @Environment(\.dismiss) private var dismiss

    init(reminder: DailyReminder, onConfirm: @escaping (Int, Int) -> Void) {
        self.onConfirm = onConfirm
        let components = DateComponents(hour: reminder.hour, minute: reminder.minute)
        _time = State(initialValue: Calendar.current.date(from: components) ?? .now)
    }

    var body: some View {
        VStack(spacing: 16) {
            DatePicker("Hora del recordatorio", selection: $time, displayedComponents: .hourAndMinute)
                .labelsHidden()
                #if os(iOS)
                .datePickerStyle(.wheel)
                #endif
                .environment(\.locale, Locale(identifier: "es_AR"))
            HStack {
                Button("Cancelar") { dismiss() }
                Spacer()
                Button("Listo") {
                    let parts = Calendar.current.dateComponents([.hour, .minute], from: time)
                    onConfirm(parts.hour ?? 21, parts.minute ?? 30)
                    dismiss()
                }
                .fontWeight(.semibold)
            }
            .font(.app(16))
            .foregroundStyle(MarginColors.ink)
        }
        .padding(24)
        .presentationDetents([.height(320)])
        .background(MarginColors.paper)
    }
}

/// "Ya estás adentro", O6 (ONB-013).
struct AllSetView: View {
    @State private var viewModel: AllSetViewModel
    let onStartReading: () -> Void
    let onImportBooks: () -> Void

    init(viewModel: AllSetViewModel, onStartReading: @escaping () -> Void, onImportBooks: @escaping () -> Void) {
        _viewModel = State(initialValue: viewModel)
        self.onStartReading = onStartReading
        self.onImportBooks = onImportBooks
    }

    var body: some View {
        let vm = viewModel
        OnboardingPage(background: MarginColors.yellow) {
            Spacer().frame(height: 56)
            OnbTitle(text: vm.name.map { "Ya estás adentro,\n\($0)." } ?? "Ya estás\nadentro.", size: 54, padding: 28)
                .minimumScaleFactor(0.6)
            Text(summary(vm))
                .font(.app(15))
                .foregroundStyle(OnbColors.softOnYellow)
                .lineSpacing(5)
                .frame(maxWidth: 270, alignment: .leading)
                .fixedSize(horizontal: false, vertical: true)
                .padding(.horizontal, 28)
                .padding(.top, 16)
        } bottom: {
            VStack(spacing: 10) {
                PrimaryButton(text: "Empezar a leer", action: onStartReading)
                SecondaryButton(text: "Importar mis libros", leading: {
                    Image(systemName: "plus").font(.system(size: 17, weight: .semibold))
                }, action: onImportBooks)
            }
            .padding(.horizontal, 20)
            .padding(.bottom, 16)
        }
        .task { await vm.load() }
    }

    private func summary(_ vm: AllSetViewModel) -> String {
        guard let goal = vm.goal else { return "Tu biblioteca te espera. Importá tus EPUB y empezá cuando quieras." }
        guard let reminder = vm.reminder, reminder.enabled else { return "\(goal.minutes) minutos por día." }
        return "\(goal.minutes) minutos por día, recordatorio a las \(formatTime(hour: reminder.hour, minute: reminder.minute))."
    }
}
