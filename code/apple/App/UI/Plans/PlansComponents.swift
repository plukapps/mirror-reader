import ReaderDomain
import SwiftUI

// Piezas compartidas de Planes (diseño `Margin Planes.dc.html`): px del HTML = pt.

/// Ícono de Material Symbols Rounded de `Assets.xcassets/Plans`.
struct PlanIcon: View {
    let name: String
    var size: CGFloat = 22
    var color: Color = MarginColors.ink

    var body: some View {
        Image("Plans\(name)")
            .renderingMode(.template)
            .resizable()
            .frame(width: size, height: size)
            .foregroundStyle(color)
            .accessibilityHidden(true)
    }
}

/// Barra de uso: 6 de alto, radio 3, pista `off` y relleno tinta (PLN-003, PLN-011, PLN-012).
struct PlanUsageBar: View {
    let fraction: Double
    var fill: Color = MarginColors.ink
    var track: Color = MarginColors.off

    var body: some View {
        GeometryReader { proxy in
            ZStack(alignment: .leading) {
                Capsule().fill(track)
                if fraction > 0 {
                    Capsule().fill(fill).frame(width: max(proxy.size.width * fraction, 6))
                }
            }
        }
        .frame(height: 6)
        .accessibilityHidden(true)
    }
}

/// "Tu plan": alto 28, radio 14, borde de 1,5 del color del texto (tinta por defecto), con o sin tilde.
struct YourPlanChip: View {
    var check = true
    var height: CGFloat = 28
    var fontSize: CGFloat = 12
    var color: Color = MarginColors.ink

    var body: some View {
        HStack(spacing: 4) {
            if check { PlanIcon(name: "Check", size: 16, color: color) }
            Text("Tu plan").font(.app(fontSize, .bold)).foregroundStyle(color)
        }
        .padding(.horizontal, height == 28 ? 10 : 12)
        .frame(height: height)
        .overlay(Capsule().strokeBorder(color, lineWidth: 1.5))
    }
}

/// Botón principal tinta: alto 56, radio 28, texto amarillo 16 bold, con flecha opcional.
struct PlanPrimaryButton: View {
    let title: String
    var arrow = false
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 8) {
                Text(title).font(.app(16, .bold)).lineLimit(1).minimumScaleFactor(0.8)
                if arrow { ProfileIcon(name: "ArrowForward", size: 20, color: MarginColors.yellow) }
            }
            .foregroundStyle(MarginColors.yellow)
            .padding(.horizontal, 28)
            .frame(maxWidth: .infinity, minHeight: 56)
            .background(MarginColors.inkFill, in: Capsule())
            .contentShape(Capsule())
        }
        .buttonStyle(.plain)
    }
}

/// PLN-015: marca chica de una opción que es de un plan pago ("Lector"): amarillo con texto tinta, no cambia con el modo.
struct PlanMark: View {
    var plan: PlanId = .plus

    var body: some View {
        Text(plan.name)
            .font(.app(9, .bold))
            .foregroundStyle(MarginColors.onYellow)
            .padding(.horizontal, 5)
            .padding(.vertical, 2)
            .background(MarginColors.yellow, in: Capsule())
            .fixedSize()
            .accessibilityHidden(true)
    }
}

extension Font {
    /// JetBrains Mono (ADR 0009) para las proporciones y tamaños.
    static func mono(_ size: CGFloat, _ weight: Font.Weight = .medium) -> Font {
        .custom("JetBrains Mono", size: size).weight(weight)
    }

    /// Newsreader en itálica, la frase de "Si cancelás…".
    static func serifItalic(_ size: CGFloat) -> Font {
        .custom("Newsreader", size: size).italic()
    }
}
