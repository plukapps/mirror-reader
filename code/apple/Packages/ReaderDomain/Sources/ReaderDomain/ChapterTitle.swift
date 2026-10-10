/// Entrada de la tabla de contenidos, a cualquier nivel, en el orden en que aparece: título y recurso (puede traer fragmento).
public struct ChapterEntry: Equatable, Sendable {
    public let title: String
    public let href: String

    public init(title: String, href: String) {
        self.title = title
        self.href = href
    }
}

/// Título del capítulo donde está el lector (RDR-013), o vacío si no se puede deducir. Igual que Android.
///
/// `toc` es la tabla de contenidos aplanada y `readingOrder` la lista de recursos del libro. El capítulo es la
/// última entrada cuyo recurso no viene después del actual: un recurso sin entrada propia (continuación) sigue
/// en el capítulo anterior. Si varias entradas apuntan al mismo recurso gana la de fragmento exacto, y si no la primera.
public func currentChapterTitle(toc: [ChapterEntry], readingOrder: [String], href: String) -> String {
    guard let current = readingOrder.firstIndex(of: resource(of: href)) else { return "" }
    let located = toc.compactMap { entry in
        readingOrder.firstIndex(of: resource(of: entry.href)).map { ($0, entry) }
    }
    guard let bestIndex = located.map(\.0).filter({ $0 <= current }).max() else { return "" }
    let sameResource = located.filter { $0.0 == bestIndex }.map(\.1)
    let hasFragment = href.contains("#") && !fragment(of: href).isEmpty
    let exact = bestIndex == current && hasFragment ? sameResource.first { $0.href == href } : nil
    return (exact ?? sameResource[0]).title
}

/// El recurso de un href, sin el fragmento (`c2.xhtml#p5` → `c2.xhtml`).
func resource(of href: String) -> String {
    String(href.prefix { $0 != "#" })
}

private func fragment(of href: String) -> Substring {
    guard let hash = href.firstIndex(of: "#") else { return "" }
    return href[href.index(after: hash)...]
}
