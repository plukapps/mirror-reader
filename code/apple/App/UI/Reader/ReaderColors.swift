import ReaderDomain
import SwiftUI

/// Colores del lector por tema, los mismos que `ReaderScreen` de Android (diseño, pantallas 06 a 08).
/// El fondo y el texto de la página son los de los temas de Readium, para que la barra y el pie no se noten.
struct ReaderColors {
    let page: Color
    let text: Color
    /// Íconos y "Aa" de la barra superior.
    let ink: Color
    /// Título del capítulo.
    let muted: Color

    init(_ theme: ReadingTheme) {
        switch theme {
        case .light:
            page = Color(hex: 0xFFFFFF); text = Color(hex: 0x121212); ink = Color(hex: 0x130000); muted = Color(hex: 0x6B6650)
        case .sepia:
            page = Color(hex: 0xFAF4E8); text = Color(hex: 0x121212); ink = Color(hex: 0x3B2F1E); muted = Color(hex: 0x7A6A50)
        case .dark:
            page = Color(hex: 0x000000); text = Color(hex: 0xFEFEFE); ink = Color(hex: 0xCFCAC0); muted = Color(hex: 0x8A8676)
        }
    }
}

/// Panel de ajustes (RDR-014): siempre oscuro, como el diseño.
enum SheetColors {
    static let background = Color(hex: 0x130000)
    static let ink = Color(hex: 0xFDFDFD)
    static let border = Color(hex: 0x4A4840)
    static let label = Color(hex: 0xA8A496)
    static let muted = Color(hex: 0x8A8676)
    static let selected = Color(hex: 0x33322D)
    static let accent = MarginColors.yellow
}
