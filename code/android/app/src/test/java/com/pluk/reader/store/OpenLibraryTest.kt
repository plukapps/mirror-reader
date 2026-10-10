package com.pluk.reader.store

import com.pluk.reader.data.store.deliverFirst
import com.pluk.reader.data.store.openLibrarySearchUrl
import com.pluk.reader.data.store.parseOpenLibrarySearch
import com.pluk.reader.domain.model.ImportOutcome
import com.pluk.reader.domain.store.CatalogWork
import com.pluk.reader.domain.store.DeliveryOutcome
import java.io.File
import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenLibraryTest {
    // STO-001, STO-006: título y autor con `q`, solo autor con `author`; la consulta va codificada
    @Test
    fun searchUrl() {
        val all = openLibrarySearchUrl("orgullo y prejuicio", byAuthor = false)
        assertTrue(all, all.startsWith("https://openlibrary.org/search.json?q=orgullo+y+prejuicio&"))
        assertTrue(all, "fields=key,title,author_name,cover_i,ebook_access,id_standard_ebooks,id_project_gutenberg" in all)
        assertTrue(all, "limit=20" in all)
        val authors = openLibrarySearchUrl("séneca&x", byAuthor = true)
        assertTrue(authors, authors.startsWith("https://openlibrary.org/search.json?author=s%C3%A9neca%26x&"))
    }

    // Respuesta recortada de search.json?q=frankenstein (2026-10-10)
    private val response = """
        {"numFound": 3380, "docs": [
          {"author_name": ["Mary Shelley"], "cover_i": 12356249, "ebook_access": "public",
           "key": "/works/OL450063W", "title": "Frankenstein; or, The Modern Prometheus",
           "id_standard_ebooks": ["mary-shelley/frankenstein", "mary-shelley/frankenstein"]},
          {"author_name": ["Mary Shelley"], "cover_i": 7267770, "ebook_access": "public",
           "key": "/works/OL25595002W", "title": "Mary Shelley's Frankenstein (1818 text)",
           "id_project_gutenberg": ["41445"]},
          {"author_name": ["Dean Koontz", "Kevin J. Anderson"], "ebook_access": "borrowable",
           "key": "/works/OL497167W", "title": "Dean Koontz's Frankenstein"},
          {"key": "/works/OL1W"}
        ]}
    """.trimIndent()

    @Test
    fun parsesWorks() {
        val works = parseOpenLibrarySearch(response)
        assertEquals(
            CatalogWork(
                key = "/works/OL450063W",
                title = "Frankenstein; or, The Modern Prometheus",
                authors = listOf("Mary Shelley"),
                coverId = 12356249,
                standardEbooksIds = listOf("mary-shelley/frankenstein", "mary-shelley/frankenstein"),
                gutenbergIds = emptyList(),
            ),
            works[0],
        )
        assertEquals(listOf("41445"), works[1].gutenbergIds)
        assertEquals(listOf("Dean Koontz", "Kevin J. Anderson"), works[2].authors)
        assertEquals(null, works[2].coverId)
        // Sin título no se muestra.
        assertEquals(3, works.size)
    }

    // STO-005: una edición de préstamo (con DRM) nunca trae fuente aunque traiga ids
    @Test
    fun borrowableWorksHaveNoSources() {
        val json = """{"docs": [{"key": "/works/OL9W", "title": "X", "ebook_access": "borrowable", "id_project_gutenberg": ["1"]}]}"""
        assertTrue(parseOpenLibrarySearch(json).single().gutenbergIds.isEmpty())
    }

    @Test
    fun emptyOrBrokenResponse() {
        assertTrue(parseOpenLibrarySearch("""{"docs": []}""").isEmpty())
        assertTrue(parseOpenLibrarySearch("{}").isEmpty())
    }

    // STO-003: prueba las fuentes en orden, se queda con la primera que se importa y borra los temporales
    @Test
    fun deliverTriesSourcesInOrder() = runTest {
        val temps = mutableListOf<File>()
        val tried = mutableListOf<String>()
        val outcome = deliverFirst(
            sources = listOf("a", "b", "c"),
            download = { url ->
                tried += url
                if (url == "a") throw IOException("404")
                File.createTempFile("store-", ".epub").also { temps += it }
            },
            import = { file -> if (temps.indexOf(file) == 0) ImportOutcome.Rejected("DRM") else ImportOutcome.Imported("hash", "T") },
        )
        assertEquals(DeliveryOutcome.Delivered("hash"), outcome)
        assertEquals(listOf("a", "b", "c"), tried)
        assertTrue(temps.none { it.exists() })
    }

    // STO-008: si ya estaba en la biblioteca, también es una entrega
    @Test
    fun alreadyInLibraryIsDelivered() = runTest {
        val outcome = deliverFirst(
            sources = listOf("a"),
            download = { File.createTempFile("store-", ".epub") },
            import = { ImportOutcome.AlreadyInLibrary("hash", "T") },
        )
        assertEquals(DeliveryOutcome.Delivered("hash"), outcome)
    }

    @Test
    fun allSourcesFail() = runTest {
        val outcome = deliverFirst(sources = listOf("a"), download = { throw IOException("sin red") }, import = { error("no") })
        assertTrue(outcome is DeliveryOutcome.Failed)
        assertFalse((outcome as DeliveryOutcome.Failed).message.isBlank())
    }
}
