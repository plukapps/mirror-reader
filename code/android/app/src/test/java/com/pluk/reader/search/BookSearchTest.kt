package com.pluk.reader.search

import com.pluk.reader.domain.model.LibraryBook
import com.pluk.reader.domain.search.SearchScope
import com.pluk.reader.domain.search.normalizeForSearch
import com.pluk.reader.domain.search.searchBooks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BookSearchTest {
    private fun book(id: String, title: String, author: String?) = LibraryBook(id, title, author, null, null)

    private val meditations = book("med", "Meditaciones", "Marco Aurelio")
    private val letters = book("let", "Cartas a Lucilio", "Séneca")
    private val about = book("abo", "Vida de Séneca", "Pierre Grimal")
    private val noAuthor = book("noa", "Diario", null)
    private val library = listOf(meditations, letters, about, noAuthor)

    private fun ids(query: String, scope: SearchScope = SearchScope.All) =
        searchBooks(library, query, scope).map { it.id }

    // LIB-013
    @Test
    fun ignoresCaseAndAccents() {
        assertEquals(listOf("med"), ids("MEDITACIÓN"))
        assertEquals(listOf("med"), ids("aurelio"))
        // "Vida de Séneca" lo tiene en el título: va antes que "Cartas a Lucilio", que lo tiene en el autor.
        assertEquals(listOf("abo", "let"), ids("seneca"))
    }

    // LIB-013
    @Test
    fun normalizesText() {
        assertEquals("seneca cartas", normalizeForSearch("  Séneca   CARTAS "))
        assertEquals("nino", normalizeForSearch("Niño"))
    }

    // LIB-013: cada palabra tiene que estar en el título o en el autor
    @Test
    fun everyWordMustMatchTitleOrAuthor() {
        assertEquals(listOf("let"), ids("seneca cartas"))
        assertEquals(emptyList<String>(), ids("seneca diario"))
    }

    // LIB-015: "Autores" solo mira el autor
    @Test
    fun authorsScopeOnlyMatchesTheAuthor() {
        assertEquals(listOf("let"), ids("seneca", SearchScope.Authors))
        assertEquals(emptyList<String>(), ids("diario", SearchScope.Authors))
    }

    // LIB-015: primero los títulos que empiezan con lo buscado, después los que lo contienen, después solo autor
    @Test
    fun ordersByTitlePrefixThenTitleThenAuthor() {
        val books = listOf(
            book("author", "Otro libro", "Ana Vida"),
            book("contains", "Una vida", null),
            book("prefix", "Vida breve", null),
        )
        assertEquals(listOf("prefix", "contains", "author"), searchBooks(books, "vida").map { it.id })
    }

    // LIB-015: dentro de cada grupo se respeta el orden de la biblioteca
    @Test
    fun keepsLibraryOrderWithinAGroup() {
        val books = listOf(book("b", "Vida B", null), book("a", "Vida A", null))
        assertEquals(listOf("b", "a"), searchBooks(books, "vida").map { it.id })
    }

    @Test
    fun blankQueryHasNoResults() {
        assertTrue(searchBooks(library, "   ").isEmpty())
    }

    @Test
    fun noMatchesIsEmpty() {
        assertTrue(searchBooks(library, "zzz").isEmpty())
    }
}
