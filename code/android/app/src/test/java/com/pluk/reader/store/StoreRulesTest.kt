package com.pluk.reader.store

import com.pluk.reader.domain.model.LibraryBook
import com.pluk.reader.domain.store.Acquisition
import com.pluk.reader.domain.store.CatalogWork
import com.pluk.reader.domain.store.SearchFilter
import com.pluk.reader.domain.store.StoreBook
import com.pluk.reader.domain.store.catalogResults
import com.pluk.reader.domain.store.epubSources
import com.pluk.reader.domain.store.libraryResults
import com.pluk.reader.domain.store.testPriceCents
import com.pluk.reader.domain.store.toStoreBook
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StoreRulesTest {
    private fun work(
        key: String,
        standard: List<String> = emptyList(),
        gutenberg: List<String> = emptyList(),
    ) = CatalogWork(key, "Título $key", listOf("Autora"), coverId = null, standardEbooksIds = standard, gutenbergIds = gutenberg)

    private fun store(key: String, acquisition: Acquisition) =
        StoreBook(key, "Título $key", listOf("Autora"), coverUrl = null, acquisition = acquisition)

    // STO-005: Standard Ebooks primero (mejor edición), después Gutenberg
    @Test
    fun prefersStandardEbooksThenGutenberg() {
        val sources = epubSources(standardEbooksIds = listOf("mary-shelley/frankenstein"), gutenbergIds = listOf("84", "41445"))
        assertEquals(
            listOf(
                "https://standardebooks.org/ebooks/mary-shelley/frankenstein/downloads/mary-shelley_frankenstein.epub?source=download",
                "https://www.gutenberg.org/ebooks/84.epub3.images",
                "https://www.gutenberg.org/ebooks/41445.epub3.images",
            ),
            sources,
        )
    }

    // STO-005: ids repetidos o con formato inesperado no generan URL
    @Test
    fun ignoresDuplicatedAndMalformedIds() {
        val sources = epubSources(
            standardEbooksIds = listOf("mary-shelley/frankenstein", "mary-shelley/frankenstein", "../x", ""),
            gutenbergIds = listOf("84", "84", "abc"),
        )
        assertEquals(2, sources.size)
    }

    // STO-002: sin edición sin DRM, no disponible
    @Test
    fun workWithoutSourcesIsUnavailable() {
        assertEquals(Acquisition.Unavailable, work("/works/OL1W").toStoreBook().acquisition)
    }

    // STO-005: el precio de prueba es estable y solo lo lleva una parte de los libros
    @Test
    fun testPriceIsStableAndPartial() {
        val keys = (1..300).map { "/works/OL${it}W" }
        val prices = keys.map { testPriceCents(it) }
        assertEquals(prices, keys.map { testPriceCents(it) })
        val paid = prices.filterNotNull()
        assertTrue("algunos con precio", paid.isNotEmpty())
        assertTrue("la mayoría gratis", paid.size < keys.size / 2)
        assertTrue(paid.all { it in setOf(299, 499, 799) })
    }

    // STO-002, STO-005: con fuente y precio de prueba, se compra; sin precio, gratis
    @Test
    fun acquisitionFollowsTestPrice() {
        val keys = (1..50).map { "/works/OL${it}W" }
        val paidKey = keys.first { testPriceCents(it) != null }
        val freeKey = keys.first { testPriceCents(it) == null }
        val paid = work(paidKey, gutenberg = listOf("84")).toStoreBook().acquisition
        val free = work(freeKey, gutenberg = listOf("84")).toStoreBook().acquisition
        assertEquals(Acquisition.Paid(listOf("https://www.gutenberg.org/ebooks/84.epub3.images"), testPriceCents(paidKey)!!), paid)
        assertEquals(Acquisition.Free(listOf("https://www.gutenberg.org/ebooks/84.epub3.images")), free)
    }

    @Test
    fun coverUrlFromCoverId() {
        val book = work("/works/OL1W").copy(coverId = 12356249).toStoreBook()
        assertEquals("https://covers.openlibrary.org/b/id/12356249-M.jpg", book.coverUrl)
        assertNull(work("/works/OL2W").toStoreBook().coverUrl)
    }

    private val free = store("/works/A", Acquisition.Free(listOf("a")))
    private val paid = store("/works/B", Acquisition.Paid(listOf("b"), 499))
    private val unavailable = store("/works/C", Acquisition.Unavailable)
    private val catalog = listOf(free, paid, unavailable)

    // STO-006: "Todo" y "Autores" muestran todo el catálogo; "Gratis" solo lo gratis; "En mi biblioteca", nada
    @Test
    fun catalogFollowsFilter() {
        assertEquals(catalog, catalogResults(catalog, SearchFilter.All, emptyMap(), emptySet()))
        assertEquals(catalog, catalogResults(catalog, SearchFilter.Authors, emptyMap(), emptySet()))
        assertEquals(listOf(free), catalogResults(catalog, SearchFilter.Free, emptyMap(), emptySet()))
        assertTrue(catalogResults(catalog, SearchFilter.InLibrary, emptyMap(), emptySet()).isEmpty())
    }

    // STO-008: lo obtenido que sigue en la biblioteca no se repite en el catálogo; si se borró, vuelve
    @Test
    fun acquiredBooksInLibraryLeaveTheCatalog() {
        val acquired = mapOf("/works/A" to "hash-a", "/works/B" to "hash-b")
        val result = catalogResults(catalog, SearchFilter.All, acquired, libraryIds = setOf("hash-a"))
        assertEquals(listOf(paid, unavailable), result)
    }

    // STO-006: la parte de la biblioteca usa la búsqueda local (LIB-015)
    @Test
    fun libraryFollowsFilter() {
        val books = listOf(
            LibraryBook("let", "Cartas a Lucilio", "Séneca", null, null),
            LibraryBook("abo", "Vida de Séneca", "Pierre Grimal", null, null),
        )
        assertEquals(listOf("abo", "let"), libraryResults(books, "seneca", SearchFilter.All).map { it.id })
        assertEquals(listOf("abo", "let"), libraryResults(books, "seneca", SearchFilter.InLibrary).map { it.id })
        assertEquals(listOf("let"), libraryResults(books, "seneca", SearchFilter.Authors).map { it.id })
        assertTrue(libraryResults(books, "seneca", SearchFilter.Free).isEmpty())
    }
}
