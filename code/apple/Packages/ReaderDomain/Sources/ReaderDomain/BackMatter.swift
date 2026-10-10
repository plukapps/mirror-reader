import Foundation

/// Entrada de primer nivel de la tabla de contenidos: título y recurso al que apunta (puede traer fragmento).
public struct BodyEntry: Equatable, Sendable {
    public let title: String
    public let href: String

    public init(title: String, href: String) {
        self.title = title
        self.href = href
    }
}

// Palabras que, en una entrada final de la tabla de contenidos, marcan páginas que no son parte de la lectura.
// Se busca la palabra dentro del título (ya normalizado), no el título exacto: "Illustration Credits", "About the Authors".
// Misma lista que Android.
private let backMatterPattern =
    "\\b(indice|index|notas?|notes?|bibliografia|bibliography|glosario|glossary|agradecimientos?|acknowledg\\w*|"
    + "creditos?|credits?|colofon|colophon|copyright|laminas|sobre (el|la|los|las) autor\\w*|acerca (del|de la|de los) autor\\w*|"
    + "about the authors?|proxima lectura|next read|also by|otros libros)\\b"

private func isBackMatter(_ title: String) -> Bool {
    normalizedTitle(title).range(of: backMatterPattern, options: .regularExpression) != nil
}

/// Minúsculas, sin acentos, solo letras, números y espacios simples.
private func normalizedTitle(_ title: String) -> String {
    let plain = title.lowercased().folding(options: .diacriticInsensitive, locale: nil)
    let spaced = plain.unicodeScalars.map { scalar -> Character in
        ("a"..."z").contains(scalar) || ("0"..."9").contains(scalar) || scalar == " " ? Character(scalar) : " "
    }
    return String(spaced).split(separator: " ").joined(separator: " ")
}

/// Índice, dentro de `readingOrder`, del primer recurso de las páginas finales del libro (RDR-012), o nil.
///
/// Son páginas finales las entradas de primer nivel al final de `toc` cuyo título contiene palabras como "notas",
/// "índice" o "créditos". Si todo el contenido es de ese tipo, o el recurso no está en el orden de lectura, no hay
/// fin del cuerpo.
public func backMatterStart(toc: [BodyEntry], readingOrder: [String]) -> Int? {
    let trailing = toc.reversed().prefix { isBackMatter($0.title) }
    guard let first = trailing.last, trailing.count < toc.count else { return nil }
    guard let index = readingOrder.firstIndex(of: resource(of: first.href)), index > 0 else { return nil }
    return index
}

/// Decide cuándo avisar que el cuerpo del libro terminó (RDR-012).
///
/// Avisa una vez, al pasar de un recurso del cuerpo a uno de las páginas finales. Si la primera posición ya está
/// dentro de las páginas finales (libro reabierto ahí), no avisa.
public final class BodyEndDetector {
    private let readingOrder: [String]
    private let backMatterStart: Int?
    private var seenBody = false
    private var notified = false

    public init(readingOrder: [String], backMatterStart: Int?) {
        self.readingOrder = readingOrder
        self.backMatterStart = backMatterStart
    }

    /// `true` si esta posición es el momento de avisar.
    public func onResource(_ href: String) -> Bool {
        guard let start = backMatterStart, let index = readingOrder.firstIndex(of: resource(of: href)) else {
            return false
        }
        if index < start {
            seenBody = true
            return false
        }
        guard seenBody, !notified else { return false }
        notified = true
        return true
    }
}

/// Texto del número de página del pie (RDR-010): la posición del libro, sin total. Nil mientras no hay dato.
public func pageLabel(position: Int?) -> String? {
    position.map(String.init)
}
