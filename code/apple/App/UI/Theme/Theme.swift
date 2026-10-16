import CoreText
import SwiftUI
#if os(iOS)
import UIKit
#else
import AppKit
#endif

/// Paleta "Editorial bold" del diseño (`design/Margin Ebook App.dc.html`), igual que `MarginColors` de Android. Los
/// colores son dinámicos: tienen un valor claro y uno oscuro (PRF-021, PRF-022) y se resuelven con el `colorScheme` del
/// entorno. Los que van sobre amarillo, el amarillo, el rojo y las portadas no cambian. Tabla de tokens en el plan
/// `2026-10-13-profile-settings.md`.
enum MarginColors {
    /// Texto e íconos principales.
    static let ink = Color(light: 0x130000, dark: 0xFDFDFD)
    /// Rellenos oscuros: barra inferior, elegidos, "GRATIS", tarjeta de ayuda, avisos. En claro es igual a `ink`.
    static let inkFill = Color(light: 0x130000, dark: 0x2B2421)
    static let yellow = Color(hex: 0xFBD256)
    /// Texto e íconos sobre amarillo: no cambian con el modo.
    static let onYellow = Color(hex: 0x130000)
    /// Texto claro sobre `inkFill` y sobre portadas oscuras: no cambia con el modo.
    static let onInk = Color(hex: 0xFDFDFD)
    /// Texto secundario sobre `inkFill` (autor en "Continuar leyendo"): no cambia con el modo.
    static let onInkMuted = Color(hex: 0xDDD9CE)
    /// Fondo de pantalla.
    static let paper = Color(light: 0xF1F1F1, dark: 0x130000)
    /// Títulos de sección y detalles.
    static let muted = Color(light: 0x6B6650, dark: 0xA8A496)
    /// Bordes.
    static let line = Color(light: 0xDDD9CE, dark: 0x4A4840)
    static let coverBorder = Color.white
    /// Íconos inactivos sobre `inkFill` (barra inferior).
    static let inkMuted = Color(hex: 0x8A8676)
    /// Tarjetas y fondo del campo de búsqueda.
    static let field = Color(light: 0xFDFDFD, dark: 0x221C19)
    /// Punto de "Leyendo" en los resultados de la búsqueda.
    static let readingDot = Color(hex: 0xC9A100)
    /// Acción destructiva: "Eliminar cuenta" (09 Profile), como `Danger` de Android.
    static let danger = Color(hex: 0xB3261E)
    /// Separadores de tarjeta y pista de la barra de espacio (09 Profile).
    static let hairline = Color(light: 0xE7E4DC, dark: 0x33322D)
    /// Texto e íconos secundarios oscuros: candado, valores de fila, segmentos sin elegir (09).
    static let ink2 = Color(light: 0x3A3830, dark: 0xCFCAC0)
    /// Texto secundario sobre amarillo: el email de la cabecera (09).
    static let onYellowMuted = Color(hex: 0x3A3115)
    /// Interruptor apagado y borde del círculo del tema Clásico (09).
    static let off = Color(light: 0xDAD6CA, dark: 0x4A4840)
    /// Separadores sobre `inkFill`: la tarjeta de ayuda (09).
    static let inkLine = Color(light: 0x33322D, dark: 0x3D3530)
    /// Pista del selector Mensual / Anual y separadores de la tabla de planes (Margin Planes).
    static let track = Color(light: 0xE4E1D8, dark: 0x33322D)
    /// Borde de la tarjeta amarilla y su separador: tinta al 20 % sobre el amarillo, no cambia con el modo.
    static let onYellowLine = Color(hex: 0x130000).opacity(0.2)
    /// Tamaño que no entra (P02b): rojo oscuro en claro, rosado en oscuro para que se lea sobre el fondo.
    static let warn = Color(light: 0x8B1E1E, dark: 0xF2A49A)

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

    /// Color con un valor para el modo claro y otro para el oscuro. SwiftUI lo resuelve con el `colorScheme` del
    /// entorno, así que una vista con `.environment(\.colorScheme, .light)` lo ve claro (onboarding y lector).
    init(light: UInt32, dark: UInt32) {
        #if os(iOS)
        self.init(uiColor: UIColor { traits in
            UIColor(Color(hex: traits.userInterfaceStyle == .dark ? dark : light))
        })
        #else
        self.init(nsColor: NSColor(name: nil) { appearance in
            NSColor(Color(hex: appearance.bestMatch(from: [.darkAqua, .aqua]) == .darkAqua ? dark : light))
        })
        #endif
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
