package com.pluk.reader.domain.store

import com.pluk.reader.domain.model.LibraryBook
import com.pluk.reader.domain.search.SearchScope
import com.pluk.reader.domain.search.searchBooks

/**
 * Filtros de la búsqueda con tienda (STO-006, amplía LIB-015): biblioteca y catálogo, solo la
 * biblioteca, solo lo gratis del catálogo, o biblioteca y catálogo buscando por autor.
 */
enum class SearchFilter { All, InLibrary, Free, Authors }

/** Obra tal como llega del catálogo, antes de decidir cómo se obtiene. */
data class CatalogWork(
    val key: String,
    val title: String,
    val authors: List<String>,
    val coverId: Long?,
    val standardEbooksIds: List<String>,
    val gutenbergIds: List<String>,
)

/** Cómo se obtiene un libro del catálogo (STO-002). [sources] son URL de EPUB sin DRM, en orden de preferencia. */
sealed interface Acquisition {
    data class Free(val sources: List<String>) : Acquisition

    /** Prueba de concepto: el precio es de prueba y la compra, simulada (STO-004, STO-005). */
    data class Paid(val sources: List<String>, val priceCents: Int) : Acquisition

    data object Unavailable : Acquisition
}

/** Libro del catálogo listo para mostrarse (STO-002). [key] es la clave de la obra, por ejemplo `/works/OL450063W`. */
data class StoreBook(
    val key: String,
    val title: String,
    val authors: List<String>,
    val coverUrl: String?,
    val acquisition: Acquisition,
) {
    val author: String? get() = authors.joinToString(", ").ifBlank { null }
}

private val standardEbooksId = Regex("[a-z0-9-]+(/[a-z0-9-]+)+")
private val gutenbergId = Regex("[0-9]+")

/**
 * URL de EPUB sin DRM de dominio público (STO-005): primero Standard Ebooks (la mejor edición) y
 * después Project Gutenberg. Los ids con formato inesperado se descartan.
 */
fun epubSources(standardEbooksIds: List<String>, gutenbergIds: List<String>): List<String> {
    val standard = standardEbooksIds.distinct().filter { standardEbooksId.matches(it) }.map { id ->
        "https://standardebooks.org/ebooks/$id/downloads/${id.replace('/', '_')}.epub?source=download"
    }
    val gutenberg = gutenbergIds.distinct().filter { gutenbergId.matches(it) }.map { id ->
        "https://www.gutenberg.org/ebooks/$id.epub3.images"
    }
    return standard + gutenberg
}

private val testPrices = listOf(299, 499, 799)

/**
 * Precio de prueba en centavos de dólar, o null si el libro es gratis (STO-005). Estable para cada
 * obra: uno de cada tres lleva precio, para recorrer la compra simulada (STO-004).
 */
fun testPriceCents(workKey: String): Int? {
    val bucket = Math.floorMod(workKey.hashCode(), testPrices.size * 3)
    return if (bucket % 3 == 0) testPrices[bucket / 3] else null
}

fun CatalogWork.toStoreBook(): StoreBook {
    val sources = epubSources(standardEbooksIds, gutenbergIds)
    val price = testPriceCents(key)
    val acquisition = when {
        sources.isEmpty() -> Acquisition.Unavailable
        price != null -> Acquisition.Paid(sources, price)
        else -> Acquisition.Free(sources)
    }
    return StoreBook(key, title, authors, coverId?.let { "https://covers.openlibrary.org/b/id/$it-M.jpg" }, acquisition)
}

/**
 * Resultado del catálogo (STO-002). Si el libro se obtuvo de la tienda y sigue en la biblioteca,
 * [inLibrary] es ese libro: se muestra "En tu biblioteca" y tocarlo lo abre (STO-008).
 */
data class StoreResult(val book: StoreBook, val inLibrary: LibraryBook? = null)

/**
 * Parte de la biblioteca de los resultados (STO-001, STO-006): la búsqueda local de LIB-013 a
 * LIB-015, sin los libros de [exclude], que ya aparecen en la parte del catálogo (STO-008).
 */
fun libraryResults(
    books: List<LibraryBook>,
    query: String,
    filter: SearchFilter,
    exclude: Set<String> = emptySet(),
): List<LibraryBook> = when (filter) {
    SearchFilter.Free -> emptyList()
    SearchFilter.Authors -> searchBooks(books, query, SearchScope.Authors)
    SearchFilter.All, SearchFilter.InLibrary -> searchBooks(books, query, SearchScope.All)
}.filterNot { it.id in exclude }

/**
 * Parte del catálogo de los resultados (STO-001, STO-006, STO-008). [acquired] relaciona la clave de
 * la obra con el id del libro obtenido; si ese libro sigue en [library], el resultado lo lleva.
 */
fun catalogResults(
    catalog: List<StoreBook>,
    filter: SearchFilter,
    acquired: Map<String, String>,
    library: List<LibraryBook>,
): List<StoreResult> {
    if (filter == SearchFilter.InLibrary) return emptyList()
    val byId = library.associateBy { it.id }
    return catalog
        .filter { filter != SearchFilter.Free || it.acquisition is Acquisition.Free }
        .map { book -> StoreResult(book, acquired[book.key]?.let(byId::get)) }
}
