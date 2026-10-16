import ReaderDomain
import SwiftUI

// Piezas comunes del onboarding (diseño O1 a O9), como `OnboardingComponents.kt` de Android. Las medidas son las del
// diseño, en puntos. Los íconos son SF Symbols.

/// Colores del onboarding que no están en `MarginColors`.
enum OnbColors {
    static let line = MarginColors.off
    static let soft = Color(hex: 0x3A3830)
    static let softOnYellow = Color(hex: 0x3A3115)
    static let error = Color(hex: 0x8B1E1E)
    static let caption = Color(hex: 0xA8A496)
}

/// Pasos del alta (ONB-003).
let onboardingSteps = 3

/// Pantalla del onboarding: fondo de punta a punta, columna de ancho máximo, desplazable si no entra (ONB-022). `top` va
/// arriba y `bottom` queda pegado abajo cuando sobra lugar; con el teclado abierto el desplazamiento deja ver el botón.
struct OnboardingPage<Top: View, Bottom: View>: View {
    let background: Color
    @ViewBuilder let top: () -> Top
    @ViewBuilder let bottom: () -> Bottom

    init(background: Color, @ViewBuilder top: @escaping () -> Top, @ViewBuilder bottom: @escaping () -> Bottom) {
        self.background = background
        self.top = top
        self.bottom = bottom
    }

    var body: some View {
        GeometryReader { geometry in
            ScrollView {
                VStack(alignment: .leading, spacing: 0) {
                    VStack(alignment: .leading, spacing: 0, content: top)
                    Spacer(minLength: 24)
                    VStack(alignment: .leading, spacing: 0, content: bottom)
                }
                .frame(maxWidth: 560)
                .frame(minHeight: geometry.size.height, alignment: .top)
                .frame(maxWidth: .infinity)
            }
            .scrollBounceBehavior(.basedOnSize)
            #if os(iOS)
            .scrollDismissesKeyboard(.interactively)
            #endif
        }
        .background(background.ignoresSafeArea())
        .foregroundStyle(MarginColors.ink)
        #if os(iOS)
        .toolbar(.hidden, for: .navigationBar)
        #endif
        .navigationBarBackButtonHidden(true)
    }
}

extension OnboardingPage where Bottom == EmptyView {
    init(background: Color, @ViewBuilder top: @escaping () -> Top) {
        self.init(background: background, top: top, bottom: { EmptyView() })
    }
}

/// Logo y "tuck.", anunciados juntos. También arriba de Perfil (PRF-024).
struct Brand: View {
    var body: some View {
        HStack(spacing: 10) {
            // Los libros parados (WEL-009): `Logo` es el SVG del paquete recortado a su caja, en plantilla.
            Image("Logo").resizable().frame(width: 24, height: 25.8)
            Text("tuck.").font(.app(20, .medium)).tracking(-0.03 * 20)
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("tuck.")
    }
}

/// Título grande de cada paso (34 pt; O7 44 pt).
struct OnbTitle: View {
    let text: String
    var size: CGFloat = 34
    var padding: CGFloat = 20

    var body: some View {
        Text(text)
            .font(.app(size))
            .tracking(-0.04 * size)
            .fixedSize(horizontal: false, vertical: true)
            .padding(.horizontal, padding)
            .accessibilityAddTraits(.isHeader)
    }
}

struct OnbBody: View {
    let text: String

    var body: some View {
        Text(text)
            .font(.app(14))
            .foregroundStyle(OnbColors.soft)
            .lineSpacing(4)
            .fixedSize(horizontal: false, vertical: true)
            .padding(.horizontal, 20)
    }
}

/// Flecha atrás (o cruz) a la izquierda y "Paso N de 4" u "Omitir" a la derecha (ONB-003).
struct StepHeader: View {
    /// nil: el lugar queda vacío (primer paso de la pila).
    var onBack: (() -> Void)?
    var backIcon = "arrow.left"
    var backLabel = "Atrás"
    var stepLabel: String?
    var onSkip: (() -> Void)?

    var body: some View {
        HStack {
            if let onBack {
                Button(action: onBack) {
                    Image(systemName: backIcon)
                        .font(.system(size: 20, weight: .medium))
                        .frame(width: 44, height: 44)
                        .contentShape(Circle())
                }
                .buttonStyle(.plain)
                .accessibilityLabel(backLabel)
            } else {
                Color.clear.frame(width: 44, height: 44)
            }
            Spacer()
            if let onSkip {
                Button(action: onSkip) {
                    Text("Omitir").font(.app(14, .semibold)).underline()
                        .padding(.horizontal, 10).padding(.vertical, 12)
                }
                .buttonStyle(.plain)
            } else if let stepLabel {
                Text(stepLabel).font(.app(12, .semibold)).foregroundStyle(MarginColors.muted).padding(.trailing, 10)
            }
        }
        .padding(.horizontal, 10)
        .padding(.top, 4)
    }
}

func stepLabel(_ step: Int, of total: Int = onboardingSteps) -> String { "Paso \(step) de \(total)" }

/// GST-011: el alta corta desde adentro de la app, crear cuenta y verificar.
let shortOnboardingSteps = 2

/// Barra de un tramo por paso: tres en el alta (ONB-003), dos en el alta corta (GST-011).
struct StepBar: View {
    let step: Int
    var total = onboardingSteps

    var body: some View {
        HStack(spacing: 4) {
            ForEach(0..<total, id: \.self) { index in
                RoundedRectangle(cornerRadius: 2)
                    .fill(index < step ? MarginColors.ink : OnbColors.line)
                    .frame(height: 4)
            }
        }
        .padding(.horizontal, 20)
        .padding(.top, 6)
        .accessibilityHidden(true)
    }
}

/// Píldora tinta con texto amarillo (y flecha). Mientras `busy`, muestra que trabaja y no responde (ONB-021).
struct PrimaryButton: View {
    let text: String
    var enabled = true
    var busy = false
    var leading: String?
    var trailing: String? = "arrow.right"
    let action: () -> Void

    var body: some View {
        let lit = enabled || busy
        let content = lit ? MarginColors.yellow : MarginColors.muted
        Button(action: action) {
            HStack(spacing: 8) {
                if busy {
                    ProgressView().tint(content).controlSize(.small)
                } else {
                    if let leading { Image(systemName: leading).font(.system(size: 17, weight: .semibold)) }
                    Text(text).font(.app(16, .bold))
                    if let trailing { Image(systemName: trailing).font(.system(size: 17, weight: .semibold)) }
                }
            }
            .foregroundStyle(content)
            .frame(maxWidth: .infinity, minHeight: 56)
            .background(lit ? MarginColors.inkFill : OnbColors.line, in: Capsule())
            .contentShape(Capsule())
        }
        .buttonStyle(.plain)
        .disabled(!enabled || busy)
        .accessibilityLabel(text)
    }
}

/// Píldora con borde.
struct SecondaryButton<Leading: View>: View {
    let text: String
    var height: CGFloat = 56
    var enabled = true
    @ViewBuilder var leading: () -> Leading
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 8) {
                leading()
                Text(text).font(.app(height < 56 ? 14 : 16, .semibold))
            }
            .foregroundStyle(MarginColors.ink)
            .frame(maxWidth: .infinity, minHeight: height)
            .overlay(Capsule().strokeBorder(MarginColors.ink, lineWidth: 1.5))
            .contentShape(Capsule())
        }
        .buttonStyle(.plain)
        .disabled(!enabled)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(text)
        .accessibilityAddTraits(.isButton)
    }
}

extension SecondaryButton where Leading == EmptyView {
    init(text: String, height: CGFloat = 56, enabled: Bool = true, action: @escaping () -> Void) {
        self.init(text: text, height: height, enabled: enabled, leading: { EmptyView() }, action: action)
    }
}

/// Botón de Google con el círculo "G" del diseño (ONB-004, ONB-014).
struct GoogleButton: View {
    let enabled: Bool
    let action: () -> Void

    var body: some View {
        SecondaryButton(text: "Google", height: 48, enabled: enabled, leading: {
            Text("G")
                .font(.app(11, .bold))
                .foregroundStyle(MarginColors.field)
                .frame(width: 20, height: 20)
                .background(MarginColors.ink, in: Circle())
        }, action: action)
        .accessibilityLabel("Continuar con Google")
    }
}

/// Línea, texto y línea ("o con email", "o").
struct OrDivider: View {
    let text: String

    var body: some View {
        HStack(spacing: 10) {
            Rectangle().fill(OnbColors.line).frame(height: 1)
            Text(text).font(.app(12, .medium)).foregroundStyle(MarginColors.muted).fixedSize()
            Rectangle().fill(OnbColors.line).frame(height: 1)
        }
        .padding(.horizontal, 20)
    }
}

/// Qué tipo de dato lleva un campo: teclado, autocompletado y mayúsculas.
enum OnbFieldKind {
    case name, email, password, newPassword
}

/// Campo con su rótulo arriba (O2, O7, O8, O9). Borde de 2 pt tinta con foco y rojo con error; las contraseñas tienen
/// el botón de ver u ocultar (ONB-005).
struct OnbTextField: View {
    let label: String
    @Binding var text: String
    var kind: OnbFieldKind = .name
    var placeholder: String?
    var error: String?
    var visible: Binding<Bool>?
    var submitLabel: SubmitLabel = .next
    var onSubmit: () -> Void = {}
    @FocusState private var focused: Bool

    private var isPassword: Bool { kind == .password || kind == .newPassword }
    private var hidden: Bool { isPassword && visible?.wrappedValue != true }

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(label).font(.app(12, .semibold)).foregroundStyle(error != nil ? OnbColors.error : MarginColors.ink)
                .accessibilityHidden(true)
            HStack(spacing: 0) {
                field
                    .font(.app(15, .medium))
                    .focused($focused)
                    .submitLabel(submitLabel)
                    .onSubmit(onSubmit)
                    .accessibilityLabel(label)
                    .accessibilityHint(error ?? "")
                if let visible, isPassword {
                    Button { visible.wrappedValue.toggle() } label: {
                        Image(systemName: visible.wrappedValue ? "eye.slash" : "eye")
                            .font(.system(size: 17))
                            .foregroundStyle(MarginColors.muted)
                            .frame(width: 40, height: 40)
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel(visible.wrappedValue ? "Ocultar contraseña" : "Mostrar contraseña")
                }
            }
            .padding(.leading, 16)
            .padding(.trailing, isPassword ? 8 : 16)
            .frame(height: 50)
            .background(MarginColors.field, in: RoundedRectangle(cornerRadius: 16))
            .overlay(
                RoundedRectangle(cornerRadius: 16)
                    .strokeBorder(borderColor, lineWidth: error != nil || focused ? 2 : 1.5)
            )
            if let error {
                HStack(spacing: 6) {
                    Image(systemName: "exclamationmark.circle.fill").font(.system(size: 13))
                    Text(error).font(.app(12, .medium))
                }
                .foregroundStyle(OnbColors.error)
                .accessibilityElement(children: .combine)
            }
        }
    }

    private var borderColor: Color {
        if error != nil { return OnbColors.error }
        return focused ? MarginColors.ink : OnbColors.line
    }

    @ViewBuilder
    private var field: some View {
        let prompt = placeholder.map { Text($0).foregroundStyle(MarginColors.muted) }
        let base = Group {
            if hidden {
                SecureField("", text: $text, prompt: prompt)
            } else {
                TextField("", text: $text, prompt: prompt)
            }
        }
        #if os(iOS)
        switch kind {
        case .name:
            base.textContentType(.name).textInputAutocapitalization(.words)
        case .email:
            base.textContentType(.emailAddress).keyboardType(.emailAddress)
                .textInputAutocapitalization(.never).autocorrectionDisabled()
        case .password:
            base.textContentType(.password).textInputAutocapitalization(.never).autocorrectionDisabled()
        case .newPassword:
            base.textContentType(.newPassword).textInputAutocapitalization(.never).autocorrectionDisabled()
        }
        #else
        base.autocorrectionDisabled(kind != .name)
        #endif
    }
}

/// Medidor de cuatro tramos con la etiqueta (ONB-005).
struct StrengthMeter: View {
    let check: PasswordCheck

    var body: some View {
        HStack(spacing: 8) {
            HStack(spacing: 3) {
                ForEach(0..<4, id: \.self) { index in
                    RoundedRectangle(cornerRadius: 2)
                        .fill(index < check.score ? MarginColors.ink : OnbColors.line)
                        .frame(height: 3)
                }
            }
            Text(check.strength.label).font(.app(11, .semibold))
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("Seguridad de la contraseña: \(check.strength.label)")
    }
}

extension PasswordStrength {
    var label: String {
        switch self {
        case .empty, .weak: "Débil"
        case .fair: "Regular"
        case .good: "Buena"
        case .strong: "Fuerte"
        }
    }
}

/// Una regla de la contraseña con su círculo marcado o vacío (O9, ONB-018).
struct RuleRow: View {
    let text: String
    let met: Bool

    var body: some View {
        HStack(spacing: 8) {
            if met {
                Image(systemName: "checkmark")
                    .font(.system(size: 10, weight: .bold))
                    .foregroundStyle(MarginColors.yellow)
                    .frame(width: 18, height: 18)
                    .background(MarginColors.inkFill, in: Circle())
            } else {
                Circle().strokeBorder(MarginColors.muted, lineWidth: 1.5).frame(width: 18, height: 18)
            }
            Text(text).font(.app(13, .medium)).foregroundStyle(met ? MarginColors.ink : MarginColors.muted)
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("\(text), \(met ? "cumplida" : "pendiente")")
    }
}

/// Ícono sobre un cuadrado amarillo (O3, O8).
struct IconBadge: View {
    let systemName: String

    var body: some View {
        Image(systemName: systemName)
            .font(.system(size: 24, weight: .medium))
            .frame(width: 56, height: 56)
            .background(MarginColors.yellow, in: RoundedRectangle(cornerRadius: 16))
            .padding(.horizontal, 20)
            .accessibilityHidden(true)
    }
}

/// Casilla tinta con ✓ amarillo y su texto: un solo control para el lector de pantalla (ONB-004).
struct TermsCheckbox: View {
    @Binding var checked: Bool

    var body: some View {
        Button { checked.toggle() } label: {
            HStack(alignment: .top, spacing: 10) {
                ZStack {
                    if checked {
                        RoundedRectangle(cornerRadius: 6).fill(MarginColors.ink)
                        Image(systemName: "checkmark").font(.system(size: 11, weight: .bold)).foregroundStyle(MarginColors.yellow)
                    } else {
                        RoundedRectangle(cornerRadius: 6).strokeBorder(MarginColors.ink, lineWidth: 1.5)
                    }
                }
                .frame(width: 20, height: 20)
                TermsText(prefix: "Acepto los ", middle: " y la ", suffix: "")
                    .font(.app(12))
                    .foregroundStyle(OnbColors.soft)
                    .frame(maxWidth: .infinity, alignment: .leading)
            }
            .padding(.vertical, 4)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("Acepto los Términos y la Política de privacidad")
        .accessibilityAddTraits(checked ? [.isButton, .isSelected] : .isButton)
    }
}

/// Texto con "Términos" y "Política de privacidad" subrayados (ONB-001, ONB-004). Todavía no son enlaces: faltan sus
/// URL públicas (K-156, CMP-002, CMP-003).
struct TermsText: View {
    let prefix: String
    let middle: String
    let suffix: String

    var body: some View {
        Text(prefix) + Text("Términos").underline().foregroundColor(MarginColors.ink)
            + Text(middle) + Text("Política de privacidad").underline().foregroundColor(MarginColors.ink) + Text(suffix)
    }
}

/// Texto subrayado que se toca ("Crear cuenta", "¿Olvidaste tu contraseña?").
struct TextLink: View {
    let text: String
    var size: CGFloat = 14
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Text(text).font(.app(size, .semibold)).underline()
                .multilineTextAlignment(.center)
                .padding(.horizontal, 6)
                .padding(.vertical, 10)
                .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }
}

/// Botón sin fondo con un ícono ("Abrir email", "Reenviar email").
struct IconTextButton: View {
    let text: String
    let systemName: String
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 4) {
                Image(systemName: systemName).font(.system(size: 14, weight: .medium))
                Text(text).font(.app(13, .semibold))
            }
            .padding(.horizontal, 6)
            .padding(.vertical, 10)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }
}

/// Aviso en tinta abajo de la pantalla ("Enlace enviado", O8).
struct ConfirmationToast: View {
    let title: String
    let detail: String

    var body: some View {
        HStack(spacing: 12) {
            Image(systemName: "checkmark")
                .font(.system(size: 13, weight: .bold))
                .frame(width: 28, height: 28)
                .background(MarginColors.yellow, in: Circle())
            VStack(alignment: .leading, spacing: 2) {
                Text(title).font(.app(14, .semibold)).foregroundStyle(MarginColors.onInk)
                Text(detail).font(.app(12)).foregroundStyle(OnbColors.caption)
            }
            Spacer(minLength: 0)
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 14)
        .background(MarginColors.inkFill, in: RoundedRectangle(cornerRadius: 16))
        .padding(.horizontal, 12)
        .accessibilityElement(children: .combine)
    }
}

extension AuthError {
    /// Texto de cada error (ONB-015), el mismo que Android.
    var message: String {
        switch self {
        case .invalidCredentials: "Email o contraseña incorrectos."
        case .emailInUse: "Ya hay una cuenta con ese email."
        case .userDisabled: "Esta cuenta está deshabilitada."
        case .tooManyRequests: "Demasiados intentos. Esperá un rato o cambiá la contraseña."
        case .network: "Sin conexión. Revisá tu red y probá de nuevo."
        case .invalidLink: "El enlace venció o ya se usó."
        case .weakPassword: "La contraseña es demasiado débil."
        case .unknown: "Algo salió mal. Probá de nuevo."
        }
    }
}

/// "0:42"
func formatSeconds(_ seconds: Int) -> String {
    String(format: "%d:%02d", seconds / 60, seconds % 60)
}

/// "21:30"
func formatTime(hour: Int, minute: Int) -> String {
    String(format: "%02d:%02d", hour, minute)
}

/// Abre la app de correo (ONB-007, ONB-017).
@MainActor
func openEmailApp(_ openURL: OpenURLAction) {
    if let url = URL(string: "message://") { openURL(url) }
}
