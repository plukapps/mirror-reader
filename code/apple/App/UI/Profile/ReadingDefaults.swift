import ReaderDomain
import SwiftUI

/// Colores de cada tema en la vista previa y en su círculo (los del diseño, pantallas 07 y 09), como Android.
private extension ReadingTheme {
    var background: Color {
        switch self {
        case .light: MarginColors.field
        case .sepia: Color(hex: 0xF3E9D4)
        case .dark: Color(hex: 0x000000)
        }
    }

    var text: Color {
        switch self {
        case .light: MarginColors.ink
        case .sepia: Color(hex: 0x3B2F1E)
        case .dark: MarginColors.field
        }
    }

    var label: LocalizedStringKey {
        switch self {
        case .light: "Clásico"
        case .sepia: "Sepia"
        case .dark: "Noche"
        }
    }
}

/// Paleta clara de los controles de letra en Perfil: lo elegido en tinta con texto amarillo.
private let lightFontControls = FontControlsPalette(
    ink: MarginColors.ink, accent: MarginColors.ink, onAccent: MarginColors.yellow, border: MarginColors.off
)

/// PRF-011 a PRF-015: vista previa, letra, tema e interlineado.
struct ReadingDefaultsCard: View {
    let viewModel: ProfileViewModel
    let onOpenFont: () -> Void

    var body: some View {
        let reading = viewModel.reading
        ProfileCard {
            preview(reading)
            ProfileRow(action: onOpenFont) {
                Text("Letra").font(.app(15, .medium)).foregroundStyle(MarginColors.ink)
                Spacer(minLength: 0)
                Text("\(reading.font.familyName) · \(readerFontPoints(fontTenths: reading.fontTenths))")
                    .font(.custom(reading.font.familyName, size: 15).weight(.medium))
                    .foregroundStyle(MarginColors.ink2)
                ProfileIcon(name: "ChevronRight", color: MarginColors.muted)
            }
            CardDivider()
            ProfileRow(action: nil) {
                Text("Tema").font(.app(15, .medium)).foregroundStyle(MarginColors.ink)
                Spacer(minLength: 0)
                HStack(spacing: 10) {
                    ForEach([ReadingTheme.light, .sepia, .dark], id: \.self) { theme in
                        swatch(theme, selected: theme == reading.theme)
                    }
                }
            }
            CardDivider()
            ProfileRow(action: nil) {
                Text("Interlineado").font(.app(15, .medium)).foregroundStyle(MarginColors.ink)
                Spacer(minLength: 0)
                HStack(spacing: 4) {
                    spacingOption(.normal, icon: "DensityMedium", label: "Interlineado normal", selected: reading.lineSpacing)
                    spacingOption(.wide, icon: "DensityLarge", label: "Interlineado amplio", selected: reading.lineSpacing)
                }
            }
        }
    }

    /// PRF-012: párrafo de muestra con la letra, el tamaño, el tema y el interlineado elegidos.
    private func preview(_ reading: ReaderSettings) -> some View {
        let size = 16 * reading.fontScale
        return Text("Al empezar la mañana, decite: hoy me voy a cruzar con el entrometido, el desagradecido, el soberbio…")
            .font(.custom(reading.font.familyName, size: size))
            // SwiftUI suma espacio entre líneas; el interlineado del diseño es proporcional al tamaño.
            .lineSpacing(size * (reading.lineSpacing.lineHeight - 1.2))
            .foregroundStyle(reading.theme.text)
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.horizontal, 16)
            .padding(.vertical, 14)
            .background(reading.theme.background, in: RoundedRectangle(cornerRadius: 14))
            .padding(EdgeInsets(top: 12, leading: 12, bottom: 4, trailing: 12))
            .accessibilityHidden(true)
    }

    /// Círculo de 28; el elegido lleva un aro tinta separado por 2 pt, por fuera, sin mover nada.
    private func swatch(_ theme: ReadingTheme, selected: Bool) -> some View {
        Button { viewModel.setTheme(theme) } label: {
            Circle()
                .fill(theme.background)
                .overlay(Circle().stroke(theme == .light ? MarginColors.off : .clear, lineWidth: 1.5))
                .frame(width: 28, height: 28)
                .background {
                    if selected {
                        Circle().fill(MarginColors.ink).frame(width: 36, height: 36)
                            .overlay(Circle().fill(MarginColors.field).frame(width: 32, height: 32))
                    }
                }
                .contentShape(Circle())
        }
        .buttonStyle(.plain)
        .accessibilityLabel(theme.label)
        .accessibilityAddTraits(selected ? .isSelected : [])
    }

    /// Botón de 36, radio 10: el elegido en tinta con el ícono amarillo.
    private func spacingOption(_ spacing: LineSpacing, icon: String, label: LocalizedStringKey, selected: LineSpacing) -> some View {
        let isSelected = spacing == selected
        return Button { viewModel.setLineSpacing(spacing) } label: {
            ProfileIcon(name: icon, size: 20, color: isSelected ? MarginColors.yellow : MarginColors.inkMuted)
                .frame(width: 36, height: 36)
                .background(isSelected ? MarginColors.ink : .clear, in: RoundedRectangle(cornerRadius: 10))
                .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityLabel(label)
        .accessibilityAddTraits(isSelected ? .isSelected : [])
    }
}

/// PRF-013: panel claro con el tipo de letra y "A−"/"A+", los mismos controles del panel del lector.
struct FontSheet: View {
    let viewModel: ProfileViewModel

    var body: some View {
        VStack(alignment: .leading, spacing: 18) {
            Text("Letra").font(.app(15, .semibold)).foregroundStyle(MarginColors.ink)
            FontChips(selected: viewModel.reading.font, onSelect: viewModel.setFont, palette: lightFontControls)
            SizeButtons(onSmaller: viewModel.smallerFont, onBigger: viewModel.biggerFont, palette: lightFontControls)
        }
        .padding(.horizontal, 20)
        .padding(.top, 24)
        .padding(.bottom, 16)
        .frame(maxWidth: .infinity, alignment: .top)
        .background(MarginColors.field)
    }
}
