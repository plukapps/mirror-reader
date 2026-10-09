package com.pluk.reader.domain.search

import com.pluk.reader.domain.model.LibraryBook
import java.text.Normalizer

/** Filtros de la búsqueda (LIB-015): título o autor, o solo autor. */
enum class SearchScope { All, Authors }

private val combiningMarks = Regex("\\p{Mn}+")
private val whitespace = Regex("\\s+")

/** Minúsculas, sin acentos y con los espacios colapsados, para comparar sin importar cómo se escribió (LIB-013). */
fun normalizeForSearch(text: String): String =
    Normalizer.normalize(text, Normalizer.Form.NFD)
        .replace(combiningMarks, "")
        .lowercase()
        .trim()
        .replace(whitespace, " ")

/**
 * Busca en la biblioteca (LIB-006, LIB-013, LIB-015). Un libro coincide si cada palabra de [query]
 * está en su título o en su autor (con [SearchScope.Authors], solo en el autor). Primero los títulos
 * que empiezan con lo buscado, luego los que lo contienen y al final las coincidencias por autor;
 * dentro de cada grupo, el orden de [books]. Sin consulta no hay resultados.
 */
fun searchBooks(books: List<LibraryBook>, query: String, scope: SearchScope = SearchScope.All): List<LibraryBook> {
    val normalized = normalizeForSearch(query)
    if (normalized.isEmpty()) return emptyList()
    val words = normalized.split(' ')
    return books
        .mapNotNull { book ->
            val title = normalizeForSearch(book.title)
            val author = normalizeForSearch(book.author.orEmpty())
            val matches = words.all { word ->
                word in author || (scope == SearchScope.All && word in title)
            }
            if (!matches) return@mapNotNull null
            val rank = when {
                scope == SearchScope.Authors -> 0
                title.startsWith(normalized) -> 0
                words.all { it in title } -> 1
                else -> 2
            }
            rank to book
        }
        .sortedBy { it.first } // Estable: conserva el orden de la biblioteca dentro de cada grupo.
        .map { it.second }
}
