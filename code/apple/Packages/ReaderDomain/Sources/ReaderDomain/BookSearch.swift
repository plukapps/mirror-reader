import Foundation

/// Filtros de la búsqueda (LIB-015): título o autor, o solo autor.
public enum SearchScope: CaseIterable, Sendable {
    case all, authors
}

/// Minúsculas, sin acentos y con los espacios colapsados, para comparar sin importar cómo se escribió (LIB-013).
/// Igual que `normalizeForSearch` de Android.
public func normalizeForSearch(_ text: String) -> String {
    text.folding(options: [.diacriticInsensitive, .caseInsensitive], locale: nil)
        .lowercased()
        .split(whereSeparator: \.isWhitespace)
        .joined(separator: " ")
}

/// Busca en la biblioteca (LIB-006, LIB-013, LIB-015), como `searchBooks` de Android. Un libro coincide si
/// cada palabra de `query` está en su título o en su autor (con `.authors`, solo en el autor). Primero los
/// títulos que empiezan con lo buscado, luego los que lo contienen y al final las coincidencias por autor;
/// dentro de cada grupo, el orden de `books`. Sin consulta no hay resultados.
public func searchBooks(_ books: [LibraryBook], query: String, scope: SearchScope = .all) -> [LibraryBook] {
    let normalized = normalizeForSearch(query)
    guard !normalized.isEmpty else { return [] }
    let words = normalized.split(separator: " ")
    let ranked = books.compactMap { book -> (rank: Int, book: LibraryBook)? in
        let title = normalizeForSearch(book.title)
        let author = normalizeForSearch(book.author ?? "")
        let matches = words.allSatisfy { author.contains($0) || (scope == .all && title.contains($0)) }
        guard matches else { return nil }
        let rank = if scope == .authors || title.hasPrefix(normalized) {
            0
        } else if words.allSatisfy({ title.contains($0) }) {
            1
        } else {
            2
        }
        return (rank, book)
    }
    // Agrupa conservando el orden de la biblioteca dentro de cada grupo (`sorted` no es estable).
    return (0...2).flatMap { rank in ranked.filter { $0.rank == rank }.map(\.book) }
}
