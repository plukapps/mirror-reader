package com.pluk.reader.reader

import com.pluk.reader.domain.BodyEndDetector
import com.pluk.reader.domain.BodyEntry
import com.pluk.reader.domain.backMatterStart
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BackMatterTest {
    private val order = listOf("cover", "c1", "c2", "c3", "notes", "index")

    private fun toc(vararg entries: Pair<String, String>) = entries.map { BodyEntry(it.first, it.second) }

    // RDR-012: el bloque final de entradas de páginas finales marca el fin del cuerpo
    @Test
    fun startsAtFirstEntryOfTrailingBlock() {
        val toc = toc("Capítulo 1" to "c1", "Capítulo 2" to "c2", "Notas" to "notes", "Índice" to "index")
        assertEquals(4, backMatterStart(toc, order))
    }

    // RDR-012: los títulos en inglés y con mayúsculas o acentos distintos también cuentan
    @Test
    fun matchesEnglishAndIgnoresCaseAndAccents() {
        val toc = toc("Chapter 1" to "c1", "ACKNOWLEDGMENTS" to "notes", "Index" to "index")
        assertEquals(4, backMatterStart(toc, order))
        assertEquals(5, backMatterStart(toc("Capitulo" to "c1", "indice" to "index"), order))
    }

    // RDR-012: una entrada "Notas" en medio del libro no es el fin del cuerpo
    @Test
    fun ignoresMatchesThatAreNotTrailing() {
        val toc = toc("Notas" to "c1", "Capítulo 2" to "c2", "Capítulo 3" to "c3")
        assertNull(backMatterStart(toc, order))
    }

    // RDR-012: sin bloque final no hay fin del cuerpo
    @Test
    fun noBackMatterGivesNull() {
        assertNull(backMatterStart(toc("Capítulo 1" to "c1", "Capítulo 2" to "c2"), order))
    }

    // RDR-012: si todo el TOC son páginas finales, no hay cuerpo que terminar
    @Test
    fun allBackMatterGivesNull() {
        assertNull(backMatterStart(toc("Notas" to "notes", "Índice" to "index"), order))
    }

    // RDR-012: la entrada puede apuntar a un fragmento del recurso
    @Test
    fun hrefWithFragmentIsResolvedToItsResource() {
        val toc = toc("Capítulo 1" to "c1", "Notas" to "notes#inicio")
        assertEquals(4, backMatterStart(toc, order))
    }

    // RDR-012: si el recurso no está en el orden de lectura, no se puede ubicar
    @Test
    fun unknownResourceGivesNull() {
        assertNull(backMatterStart(toc("Capítulo 1" to "c1", "Notas" to "otro"), order))
    }

    private fun starts(titles: List<String>): Int? {
        val hrefs = titles.indices.map { "r$it" }
        return backMatterStart(titles.mapIndexed { i, t -> BodyEntry(t, hrefs[i]) }, hrefs)
    }

    // RDR-012: libro real, termina con una promoción tras "Notas" y "Créditos"
    @Test
    fun promoAtTheEndDoesNotBreakTheBlock() {
        val titles = listOf("Epílogo", "Agradecimientos", "Láminas", "Notas", "Créditos", "¡Encuentra aquí tu próxima lectura!")
        assertEquals(1, starts(titles))
    }

    // RDR-012: libro real en inglés, con plurales y "Illustration Credits"
    @Test
    fun pluralsAndCompoundTitlesMatch() {
        val titles = listOf("Conclusion", "Acknowledgments", "Illustration Credits", "Index", "About the Authors")
        assertEquals(1, starts(titles))
    }

    // RDR-012: libro real, termina con "Nota" en singular tras un apéndice
    @Test
    fun singularNoteMatches() {
        assertEquals(2, starts(listOf("Capítulo 20", "Apéndice B", "Nota")))
    }

    // RDR-012: un capítulo cuyo título contiene la palabra pero que no está al final no cuenta
    @Test
    fun keywordInsideBodyTitleIsIgnored() {
        assertNull(starts(listOf("Notas sobre el método", "Capítulo 2", "Capítulo 3")))
    }

    // RDR-012: al pasar del cuerpo a las notas avisa una sola vez
    @Test
    fun announcesOnceWhenEnteringBackMatter() {
        val detector = BodyEndDetector(order, backMatterStart = 4)
        assertFalse(detector.onResource("c2"))
        assertTrue(detector.onResource("notes"))
        assertFalse(detector.onResource("index"))
        assertFalse(detector.onResource("c3"))
        assertFalse(detector.onResource("notes"))
    }

    // RDR-012: reabrir el libro ya dentro de las páginas finales no avisa
    @Test
    fun doesNotAnnounceWhenFirstPositionIsInBackMatter() {
        val detector = BodyEndDetector(order, backMatterStart = 4)
        assertFalse(detector.onResource("index"))
        assertFalse(detector.onResource("notes"))
    }

    // RDR-012: sin páginas finales nunca avisa
    @Test
    fun neverAnnouncesWithoutBackMatter() {
        val detector = BodyEndDetector(order, backMatterStart = null)
        assertFalse(detector.onResource("c1"))
        assertFalse(detector.onResource("index"))
    }
}
