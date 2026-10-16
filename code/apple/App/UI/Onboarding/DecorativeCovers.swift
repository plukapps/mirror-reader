import SwiftUI

/// Portadas de ilustración del diseño (L0, banner de Inicio). No son libros: no se tocan ni se anuncian (GST-019,
/// GST-023). Colores fijos, como el amarillo de la marca: se ven igual en modo claro y oscuro.
enum CoverColors {
    static let ink = Color(hex: 0x130000)
    static let yellow = Color(hex: 0xFBD256)
    static let sage = Color(hex: 0x7A8F6A)
    static let navy = Color(hex: 0x1F3A5F)
    static let bone = Color(hex: 0xE8E2D3)
    static let paper = Color(hex: 0xFDFDFD)
}

/// Una portada con sombra, inclinada `degrees` y ubicada en (`x`, `y`) dentro de su caja.
private struct Cover<Content: View>: View {
    let x: CGFloat, y: CGFloat, width: CGFloat, height: CGFloat, degrees: Double, color: Color, corner: CGFloat, shadow: CGFloat
    @ViewBuilder var content: Content

    var body: some View {
        RoundedRectangle(cornerRadius: corner)
            .fill(color)
            .overlay(alignment: .topLeading) { content }
            .clipShape(RoundedRectangle(cornerRadius: corner))
            .frame(width: width, height: height)
            .shadow(color: .black.opacity(0.4), radius: shadow, y: shadow * 0.8)
            .rotationEffect(.degrees(degrees))
            .offset(x: x, y: y)
    }
}

private func coverTitle(_ text: String, size: CGFloat) -> some View {
    Text(text)
        .font(.app(size, .heavy))
        .tracking(-0.05 * size)
        .lineSpacing(-size * 0.12)
        .foregroundStyle(CoverColors.yellow)
}

/// L0 (GST-019): tres portadas grandes, la del medio adelante.
struct ReadyCovers: View {
    var body: some View {
        ZStack(alignment: .topLeading) {
            Cover(x: 4, y: 28, width: 118, height: 176, degrees: -8, color: CoverColors.sage, corner: 8, shadow: 12) {
                Text("Letters from a Stoic").font(.custom("Newsreader", size: 17)).foregroundStyle(CoverColors.paper).padding(10)
            }
            Cover(x: 180, y: 28, width: 118, height: 176, degrees: 8, color: CoverColors.bone, corner: 8, shadow: 12) {
                Text("The\nOdyssey").font(.custom("Newsreader", size: 18).weight(.medium)).foregroundStyle(CoverColors.ink).padding(10)
            }
            Cover(x: 88, y: 6, width: 130, height: 194, degrees: 0, color: CoverColors.ink, corner: 8, shadow: 16) {
                VStack(alignment: .leading) {
                    coverTitle("MEDI-\nTATI-\nONS", size: 24)
                    Spacer()
                    Text("MARCUS AURELIUS").font(.app(8, .medium)).tracking(0.64).foregroundStyle(CoverColors.paper)
                }
                .padding(12)
                .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
            }
        }
        .frame(width: 302, height: 250, alignment: .topLeading)
        .accessibilityHidden(true)
    }
}

/// Banner de Inicio (GST-023): tres portadas chicas y una insignia amarilla con `badge` abajo a la derecha.
struct BannerCovers: View {
    let badge: String
    let border: Color

    var body: some View {
        ZStack(alignment: .topLeading) {
            Cover(x: 0, y: 14, width: 44, height: 66, degrees: -10, color: CoverColors.navy, corner: 4, shadow: 6) { EmptyView() }
            Cover(x: 38, y: 16, width: 44, height: 66, degrees: 9, color: CoverColors.sage, corner: 4, shadow: 6) { EmptyView() }
            Cover(x: 18, y: 6, width: 48, height: 72, degrees: 0, color: CoverColors.ink, corner: 4, shadow: 7) {
                coverTitle("MEDI-\nTATI-\nONS", size: 9).padding(5)
            }
            Image(systemName: badge)
                .font(.system(size: 13, weight: .semibold))
                .foregroundStyle(CoverColors.ink)
                .frame(width: 25, height: 25)
                .background(Circle().fill(CoverColors.yellow))
                .padding(2.5)
                .background(Circle().fill(border))
                .offset(x: 56, y: 66)
        }
        .frame(width: 84, height: 96, alignment: .topLeading)
        .accessibilityHidden(true)
    }
}
