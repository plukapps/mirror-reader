package com.pluk.reader.reader

import com.pluk.reader.domain.ChapterEntry
import com.pluk.reader.domain.currentChapterTitle
import org.junit.Assert.assertEquals
import org.junit.Test

/** RDR-013: la barra superior muestra el título del capítulo donde está el lector. */
class ChapterTitleTest {
    private val order = listOf("cover.xhtml", "c1.xhtml", "c1b.xhtml", "c2.xhtml", "c3.xhtml", "notes.xhtml")
    private val toc = listOf(
        ChapterEntry("Capítulo 1", "c1.xhtml"),
        ChapterEntry("Capítulo 2", "c2.xhtml"),
        ChapterEntry("Capítulo 3", "c3.xhtml"),
    )

    @Test
    fun resourceWithItsOwnEntryGivesThatTitle() {
        assertEquals("Capítulo 2", currentChapterTitle(toc, order, "c2.xhtml"))
    }

    // Un recurso sin entrada propia (continuación del capítulo) sigue en el capítulo anterior
    @Test
    fun resourceWithoutEntryBelongsToThePreviousChapter() {
        assertEquals("Capítulo 1", currentChapterTitle(toc, order, "c1b.xhtml"))
        assertEquals("Capítulo 3", currentChapterTitle(toc, order, "notes.xhtml"))
    }

    // Antes del primer capítulo (portada) no hay título
    @Test
    fun beforeTheFirstEntryThereIsNoTitle() {
        assertEquals("", currentChapterTitle(toc, order, "cover.xhtml"))
    }

    @Test
    fun bookWithoutTableOfContentsHasNoTitle() {
        assertEquals("", currentChapterTitle(emptyList(), order, "c1.xhtml"))
    }

    @Test
    fun unknownResourceHasNoTitle() {
        assertEquals("", currentChapterTitle(toc, order, "otro.xhtml"))
    }

    // La posición del lector trae fragmento ("c2.xhtml#p5"): se compara por recurso
    @Test
    fun fragmentInThePositionIsIgnoredWhenTheEntryHasNone() {
        assertEquals("Capítulo 2", currentChapterTitle(toc, order, "c2.xhtml#p5"))
    }

    // Varias entradas en un mismo recurso (secciones): con fragmento exacto gana esa; si no, la primera (el capítulo)
    @Test
    fun sectionsInTheSameResourcePreferTheExactFragmentThenTheFirst() {
        val withSections = listOf(
            ChapterEntry("Libro II", "c2.xhtml"),
            ChapterEntry("La mañana", "c2.xhtml#manana"),
            ChapterEntry("La tarde", "c2.xhtml#tarde"),
        )
        assertEquals("La tarde", currentChapterTitle(withSections, order, "c2.xhtml#tarde"))
        assertEquals("Libro II", currentChapterTitle(withSections, order, "c2.xhtml"))
        assertEquals("Libro II", currentChapterTitle(withSections, order, "c2.xhtml#otro"))
    }

    // Entradas que apuntan fuera del orden de lectura no cuentan
    @Test
    fun entriesOutsideTheReadingOrderAreIgnored() {
        val odd = listOf(ChapterEntry("Fantasma", "no-esta.xhtml"), ChapterEntry("Capítulo 1", "c1.xhtml"))
        assertEquals("Capítulo 1", currentChapterTitle(odd, order, "c1b.xhtml"))
        assertEquals("", currentChapterTitle(odd, order, "cover.xhtml"))
    }
}
