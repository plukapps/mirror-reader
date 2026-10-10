/// RDR-003: tema del lector. El valor guardado es el mismo que en Android.
public enum ReadingTheme: String, CaseIterable, Sendable {
    case light = "LIGHT", dark = "DARK", sepia = "SEPIA"
}

/// RDR-014: tipo de letra del libro. Cada valor se asocia a una fuente que viaja en la app (ADR 0009).
public enum ReaderFont: String, CaseIterable, Sendable {
    case serif = "SERIF", sans = "SANS", mono = "MONO"

    /// Nombre de la familia, igual al que se declara al motor de EPUB.
    public var familyName: String {
        switch self {
        case .serif: "Newsreader"
        case .sans: "Host Grotesk"
        case .mono: "JetBrains Mono"
        }
    }
}

/// RDR-014: interlineado del libro.
public enum LineSpacing: String, CaseIterable, Sendable {
    case normal = "NORMAL", wide = "WIDE"

    public var lineHeight: Double {
        switch self {
        case .normal: 1.4
        case .wide: 1.7
        }
    }
}

/// Ajustes de lectura (RDR-002, RDR-003, RDR-014), como `ReaderSettings` de Android. Sin la animación de página:
/// en Apple el paso de página es el del motor (plan `2026-10-09-ios-reader.md`).
public struct ReaderSettings: Equatable, Sendable {
    public var theme: ReadingTheme
    /// Tamaño en décimas (10 = 100 %), entre 5 y 25. Se guarda entero para no acumular error de coma flotante.
    public private(set) var fontTenths: Int
    public var font: ReaderFont
    public var lineSpacing: LineSpacing

    public static let minTenths = 5
    public static let maxTenths = 25

    public init(
        theme: ReadingTheme = .light,
        fontScale: Double = 1.0,
        font: ReaderFont = .serif,
        lineSpacing: LineSpacing = .normal
    ) {
        self.init(theme: theme, fontTenths: Int((fontScale * 10).rounded()), font: font, lineSpacing: lineSpacing)
    }

    public init(theme: ReadingTheme, fontTenths: Int, font: ReaderFont, lineSpacing: LineSpacing) {
        self.theme = theme
        self.fontTenths = min(max(fontTenths, Self.minTenths), Self.maxTenths)
        self.font = font
        self.lineSpacing = lineSpacing
    }

    /// Escala de la letra, 1.0 = tamaño original.
    public var fontScale: Double { Double(fontTenths) / 10 }

    public func withTheme(_ theme: ReadingTheme) -> ReaderSettings { with { $0.theme = theme } }
    public func withFont(_ font: ReaderFont) -> ReaderSettings { with { $0.font = font } }
    public func withLineSpacing(_ lineSpacing: LineSpacing) -> ReaderSettings { with { $0.lineSpacing = lineSpacing } }
    public func biggerFont() -> ReaderSettings { step(+1) }
    public func smallerFont() -> ReaderSettings { step(-1) }

    private func step(_ direction: Int) -> ReaderSettings {
        ReaderSettings(theme: theme, fontTenths: fontTenths + direction, font: font, lineSpacing: lineSpacing)
    }

    private func with(_ change: (inout ReaderSettings) -> Void) -> ReaderSettings {
        var copy = self
        change(&copy)
        return copy
    }
}

/// Dónde se guardan los ajustes de lectura. La implementación vive en la capa de datos de la app.
public protocol ReaderSettingsRepository: Sendable {
    func load() -> ReaderSettings
    func save(_ settings: ReaderSettings)
}
