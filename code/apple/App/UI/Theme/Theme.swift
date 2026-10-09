import CoreText
import SwiftUI

/// Paleta "Editorial bold" del diseño (`design/Margin Ebook App.dc.html`), igual que `MarginColors` de Android.
enum MarginColors {
    static let ink = Color(hex: 0x130000)
    static let yellow = Color(hex: 0xFBD256)
    static let paper = Color(hex: 0xF1F1F1)
    static let muted = Color(hex: 0x6B6650)
    static let line = Color(hex: 0xDDD9CE)
    static let coverBorder = Color.white
    /// Íconos inactivos sobre fondo tinta (barra inferior).
    static let inkMuted = Color(hex: 0x8A8676)

    /// Fondos de las portadas generadas cuando el EPUB no trae una.
    static let covers: [(color: Color, isLight: Bool)] = [
        (Color(hex: 0x130000), false), (Color(hex: 0x1F3A5F), false), (Color(hex: 0xFBD256), true), (Color(hex: 0xE45A3F), false),
        (Color(hex: 0x8B1E1E), false), (Color(hex: 0x7A8F6A), false), (Color(hex: 0x2F2E29), false), (Color(hex: 0xE8E2D3), true),
    ]
}

extension Color {
    init(hex: UInt32) {
        self.init(
            red: Double((hex >> 16) & 0xFF) / 255,
            green: Double((hex >> 8) & 0xFF) / 255,
            blue: Double(hex & 0xFF) / 255
        )
    }
}

/// Tipografía de la interfaz: Host Grotesk (OFL, ADR 0009), variable de Light a ExtraBold.
enum AppFont {
    static let family = "Host Grotesk"

    /// Registra las fuentes que viajan en la app. Se llama una vez al arrancar.
    static func register() {
        let urls = Bundle.main.urls(forResourcesWithExtension: "ttf", subdirectory: nil) ?? []
        CTFontManagerRegisterFontURLs(urls as CFArray, .process, true, nil)
    }
}

extension Font {
    static func app(_ size: CGFloat, _ weight: Font.Weight = .regular) -> Font {
        .custom(AppFont.family, size: size).weight(weight)
    }
}
