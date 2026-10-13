import SwiftUI

// Piezas del Perfil (diseño "09 — Profile"), las mismas medidas que Android: px del HTML = pt.

/// Título de sección: 11 semibold, mayúsculas, espaciado 0,08 em.
struct ProfileSectionTitle: View {
    let text: LocalizedStringKey
    var bottom: CGFloat = 10

    var body: some View {
        Text(text)
            .textCase(.uppercase)
            .font(.app(11, .semibold))
            .tracking(11 * 0.08)
            .foregroundStyle(MarginColors.muted)
            .padding(EdgeInsets(top: 28, leading: 24, bottom: bottom, trailing: 24))
            .frame(maxWidth: .infinity, alignment: .leading)
            .accessibilityAddTraits(.isHeader)
    }
}

/// Tarjeta de sección: margen 16 a los lados, radio 20.
struct ProfileCard<Content: View>: View {
    var background: Color = MarginColors.field
    @ViewBuilder let content: Content

    var body: some View {
        VStack(spacing: 0) { content }
            .background(background, in: RoundedRectangle(cornerRadius: 20))
            .clipShape(RoundedRectangle(cornerRadius: 20))
            .padding(.horizontal, 16)
    }
}

/// Separador de 1 pt a lo ancho de la tarjeta.
struct CardDivider: View {
    var color: Color = MarginColors.hairline

    var body: some View {
        color.frame(height: 1)
    }
}

/// Fila de tarjeta: 56 de alto mínimo, relleno 16 a los lados, separación 12. Toda la fila se puede tocar.
struct ProfileRow<Content: View>: View {
    var minHeight: CGFloat = 56
    let action: (() -> Void)?
    @ViewBuilder let content: Content

    var body: some View {
        // El relleno vertical solo se nota si el texto ocupa más de una línea (letra grande, pantalla angosta).
        let row = HStack(spacing: 12) { content }
            .padding(.horizontal, 16)
            .padding(.vertical, 8)
            .frame(maxWidth: .infinity, minHeight: minHeight, alignment: .leading)
            .contentShape(Rectangle())
        if let action {
            Button(action: action) { row }.buttonStyle(.plain)
        } else {
            row
        }
    }
}

/// Ícono de Material Symbols de `Assets.xcassets/Profile`.
struct ProfileIcon: View {
    let name: String
    var size: CGFloat = 22
    var color: Color = MarginColors.ink

    var body: some View {
        Image("Profile\(name)")
            .renderingMode(.template)
            .resizable()
            .frame(width: size, height: size)
            .foregroundStyle(color)
            .accessibilityHidden(true)
    }
}

/// Etiqueta del plan: alto 22, radio 11, tinta con texto amarillo.
struct PlanChip: View {
    let text: LocalizedStringKey

    var body: some View {
        Text(text)
            .font(.app(11, .semibold))
            .tracking(11 * 0.04)
            .foregroundStyle(MarginColors.yellow)
            .padding(.horizontal, 9)
            .frame(height: 22)
            .background(MarginColors.ink, in: Capsule())
    }
}

/// Barra de espacio: 8 de alto, radio 4, pista clara y relleno tinta.
struct StorageBar: View {
    let fraction: Double

    var body: some View {
        GeometryReader { proxy in
            ZStack(alignment: .leading) {
                Capsule().fill(MarginColors.hairline)
                if fraction > 0 {
                    Capsule().fill(MarginColors.ink).frame(width: proxy.size.width * fraction)
                }
            }
        }
        .frame(height: 8)
        .accessibilityHidden(true)
    }
}

/// Interruptor del diseño: 46×28, perilla de 20 a 4 del borde. Solo dibuja; el estado lo anuncia la fila.
struct ProfileSwitch: View {
    let isOn: Bool

    var body: some View {
        Capsule()
            .fill(isOn ? MarginColors.ink : MarginColors.off)
            .frame(width: 46, height: 28)
            .overlay(alignment: isOn ? .trailing : .leading) {
                Circle()
                    .fill(isOn ? MarginColors.yellow : MarginColors.field)
                    .frame(width: 20, height: 20)
                    .padding(4)
            }
    }
}

/// Círculo de la cabecera con la inicial: 56, tinta, letra 22 bold amarilla.
struct ProfileAvatar: View {
    let initial: String

    var body: some View {
        Text(initial)
            .font(.app(22, .bold))
            .foregroundStyle(MarginColors.yellow)
            .frame(width: 56, height: 56)
            .background(MarginColors.ink, in: Circle())
    }
}

/// Opción de un control segmentado.
struct ProfileSegment: Identifiable {
    let id: Int
    let label: LocalizedStringKey
    let icon: String
}

/// Control segmentado: pista papel radio 22, relleno 4, segmentos de 36 de alto y radio 18.
struct ProfileSegmentedControl: View {
    let segments: [ProfileSegment]
    let selected: Int
    let onSelect: (Int) -> Void

    var body: some View {
        HStack(spacing: 4) {
            ForEach(segments) { segment in
                let isSelected = segment.id == selected
                let color = isSelected ? MarginColors.yellow : MarginColors.ink2
                Button { onSelect(segment.id) } label: {
                    HStack(spacing: 6) {
                        ProfileIcon(name: segment.icon, size: 18, color: color)
                        Text(segment.label).font(.app(13, isSelected ? .semibold : .medium)).foregroundStyle(color)
                    }
                    .frame(maxWidth: .infinity, minHeight: 36)
                    .background(isSelected ? MarginColors.ink : .clear, in: RoundedRectangle(cornerRadius: 18))
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityAddTraits(isSelected ? .isSelected : [])
            }
        }
        .padding(4)
        .background(MarginColors.paper, in: RoundedRectangle(cornerRadius: 22))
    }
}
