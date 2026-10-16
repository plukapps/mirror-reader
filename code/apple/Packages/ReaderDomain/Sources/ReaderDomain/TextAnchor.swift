import Foundation

/// Texto del comienzo de la página, para volver a encontrarla en otro dispositivo aunque pagine distinto (SYN-014).
/// Va en el locator como `text`: Readium busca `highlight` en el capítulo, con `before` y `after` como contexto.
/// Igual que `TextAnchor` de Android.
public struct TextAnchor: Equatable, Sendable {
    public let before: String?
    public let highlight: String
    public let after: String?

    public init(before: String?, highlight: String, after: String?) {
        self.before = before
        self.highlight = highlight
        self.after = after
    }
}

/// Caracteres de cada parte del ancla.
public let textAnchorLength = 32

/// Arma el ancla desde `start`, el primer carácter visible de la página dentro de `window` (un tramo del texto del
/// capítulo). Salta los espacios del principio; sin texto visible devuelve nil. Los índices son UTF-16, como los de
/// JavaScript, y ningún corte parte un par sustituto. Función pura.
public func textAnchorAt(window: String, start: Int) -> TextAnchor? {
    let units = Array(window.utf16)
    guard units.indices.contains(start),
          let from = (start..<units.count).first(where: { !isWhitespace(units[$0]) })
    else { return nil }
    let to = cut(units, from + textAnchorLength)
    let afterEnd = cut(units, to + textAnchorLength)
    let beforeStart = cut(units, from - textAnchorLength)
    let before = text(units, beforeStart, from)
    let after = text(units, to, afterEnd)
    return TextAnchor(
        before: before.isEmpty ? nil : before,
        highlight: text(units, from, to),
        after: after.isEmpty ? nil : after
    )
}

/// `index` dentro del texto, corrido una unidad hacia adelante si cae en la mitad de un par sustituto.
private func cut(_ units: [UInt16], _ index: Int) -> Int {
    let i = min(max(index, 0), units.count)
    return i > 0 && i < units.count && UTF16.isTrailSurrogate(units[i]) ? i + 1 : i
}

private func text(_ units: [UInt16], _ from: Int, _ to: Int) -> String {
    String(decoding: units[from..<to], as: UTF16.self)
}

// Mismo criterio que `Char.isWhitespace()` de Kotlin para el texto de un libro.
private func isWhitespace(_ unit: UInt16) -> Bool {
    Unicode.Scalar(unit)?.properties.isWhitespace ?? false
}
