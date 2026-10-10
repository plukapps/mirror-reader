import ReaderDomain
import SwiftUI

/// Panel de ajustes de lectura (RDR-014), según la pantalla 07 del diseño y `ReaderSettingsSheet` de Android:
/// tipo de letra, tamaño, tema e interlineado, y acceso al índice. Cada cambio se aplica al libro de atrás y se
/// guarda. Sin el interruptor de animación: en Apple el paso de página es el de Readium (K-135).
struct ReaderSettingsSheet: View {
    let viewModel: ReaderViewModel

    var body: some View {
        let settings = viewModel.settings
        VStack(alignment: .leading, spacing: 18) {
            Capsule()
                .fill(SheetColors.border)
                .frame(width: 36, height: 4)
                .frame(maxWidth: .infinity)
                .padding(.top, 10)
                .accessibilityHidden(true)

            FontChips(selected: settings.font, onSelect: viewModel.setFont)
            SizeButtons(onSmaller: viewModel.smallerFont, onBigger: viewModel.biggerFont)

            HStack(alignment: .top) {
                ThemeSwatches(selected: settings.theme, onSelect: viewModel.setTheme)
                Spacer(minLength: 12)
                LineSpacingPicker(selected: settings.lineSpacing, onSelect: viewModel.setLineSpacing)
            }

            Rectangle().fill(SheetColors.selected).frame(height: 1)

            HStack {
                Text("Índice").font(.app(15, .medium)).foregroundStyle(SheetColors.ink)
                Spacer()
                Button(action: viewModel.openTableOfContents) {
                    Text("Abrir")
                        .font(.app(14, .semibold))
                        .foregroundStyle(SheetColors.ink)
                        .padding(.horizontal, 18)
                        .frame(height: 36)
                        .overlay(Capsule().stroke(SheetColors.border, lineWidth: 1.5))
                        .contentShape(Capsule())
                }
                .buttonStyle(.plain)
                .accessibilityLabel("Abrir el índice")
            }
        }
        .padding(.horizontal, 20)
        .padding(.bottom, 16)
        .frame(maxWidth: .infinity, alignment: .top)
    }
}

/// Tres opciones, cada una con su propia tipografía; la elegida en amarillo.
private struct FontChips: View {
    let selected: ReaderFont
    let onSelect: (ReaderFont) -> Void

    var body: some View {
        HStack(spacing: 8) {
            ForEach(ReaderFont.allCases, id: \.self) { font in
                let isSelected = font == selected
                Button { onSelect(font) } label: {
                    Text(font.chipLabel)
                        .font(.custom(font.familyName, size: 15))
                        .foregroundStyle(isSelected ? MarginColors.ink : SheetColors.ink)
                        .frame(maxWidth: .infinity)
                        .frame(height: 40)
                        .background(isSelected ? SheetColors.accent : .clear, in: Capsule())
                        .overlay(Capsule().stroke(isSelected ? .clear : SheetColors.border, lineWidth: 1.5))
                        .contentShape(Capsule())
                }
                .buttonStyle(.plain)
                .accessibilityAddTraits(isSelected ? .isSelected : [])
            }
        }
    }
}

private extension ReaderFont {
    var chipLabel: String {
        switch self {
        case .serif: "Newsreader"
        case .sans: "Grotesk"
        case .mono: "Mono"
        }
    }
}

/// "A−" y "A+" en una fila con borde, partida en dos (RDR-002).
private struct SizeButtons: View {
    let onSmaller: () -> Void
    let onBigger: () -> Void

    var body: some View {
        HStack(spacing: 0) {
            sizeButton("A−", size: 14, label: "Letra más chica", action: onSmaller)
            Rectangle().fill(SheetColors.border).frame(width: 1.5, height: 28)
            sizeButton("A+", size: 22, label: "Letra más grande", action: onBigger)
        }
        .frame(height: 48)
        .overlay(Capsule().stroke(SheetColors.border, lineWidth: 1.5))
    }

    private func sizeButton(_ text: String, size: CGFloat, label: LocalizedStringKey, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(text)
                .font(.custom(ReaderFont.serif.familyName, size: size))
                .foregroundStyle(SheetColors.ink)
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityLabel(label)
    }
}

/// Clásico, Sepia y Noche: un círculo del color de fondo con su nombre; el elegido con anillo amarillo (RDR-003).
private struct ThemeSwatches: View {
    let selected: ReadingTheme
    let onSelect: (ReadingTheme) -> Void

    var body: some View {
        HStack(spacing: 10) {
            ForEach([ReadingTheme.light, .sepia, .dark], id: \.self) { theme in
                let isSelected = theme == selected
                Button { onSelect(theme) } label: {
                    VStack(spacing: 4) {
                        Circle()
                            .fill(theme.swatch)
                            .overlay(Circle().stroke(SheetColors.border, lineWidth: theme == .dark ? 1 : 0))
                            .frame(width: 44, height: 44)
                            .padding(5)
                            .overlay(Circle().stroke(isSelected ? SheetColors.accent : .clear, lineWidth: 2))
                        Text(theme.label)
                            .font(.app(11, isSelected ? .semibold : .regular))
                            .foregroundStyle(isSelected ? SheetColors.accent : SheetColors.label)
                    }
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityLabel(theme.label)
                .accessibilityAddTraits(isSelected ? .isSelected : [])
            }
        }
    }
}

private extension ReadingTheme {
    var label: LocalizedStringKey {
        switch self {
        case .light: "Clásico"
        case .sepia: "Sepia"
        case .dark: "Noche"
        }
    }

    var swatch: Color {
        switch self {
        case .light: Color(hex: 0xFDFDFD)
        case .sepia: Color(hex: 0xF3E9D4)
        case .dark: Color(hex: 0x000000)
        }
    }
}

/// Amplio y normal, con el ícono de tres líneas más o menos separadas, como Android.
private struct LineSpacingPicker: View {
    let selected: LineSpacing
    let onSelect: (LineSpacing) -> Void

    var body: some View {
        VStack(spacing: 4) {
            HStack(spacing: 4) {
                option(.wide, label: "Interlineado amplio")
                option(.normal, label: "Interlineado normal")
            }
            .frame(height: 54)
            Text("Interlineado").font(.app(11)).foregroundStyle(SheetColors.label)
        }
    }

    private func option(_ spacing: LineSpacing, label: LocalizedStringKey) -> some View {
        let isSelected = spacing == selected
        return Button { onSelect(spacing) } label: {
            LinesIcon(tops: spacing == .wide ? [4, 11, 18] : [7, 11, 15])
                .fill(isSelected ? SheetColors.ink : SheetColors.muted)
                .frame(width: 24, height: 24)
                .frame(width: 36, height: 36)
                .background(isSelected ? SheetColors.selected : .clear, in: RoundedRectangle(cornerRadius: 10))
                .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityLabel(label)
        .accessibilityAddTraits(isSelected ? .isSelected : [])
    }
}

/// Tres barras de 16 × 2 en un cuadro de 24, en las alturas dadas (mismas medidas que `ReaderIcons` de Android).
private struct LinesIcon: Shape {
    let tops: [CGFloat]

    func path(in rect: CGRect) -> Path {
        let scale = rect.width / 24
        var path = Path()
        for top in tops {
            path.addRect(CGRect(x: 4 * scale, y: top * scale, width: 16 * scale, height: 2 * scale))
        }
        return path
    }
}
