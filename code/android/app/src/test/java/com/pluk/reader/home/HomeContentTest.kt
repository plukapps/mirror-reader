package com.pluk.reader.home

import com.pluk.reader.domain.home.Greeting
import com.pluk.reader.domain.home.greetingFor
import com.pluk.reader.domain.home.homeContent
import com.pluk.reader.domain.model.LibraryBook
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HomeContentTest {
    private fun book(id: String, progress: Int?, lastReadAt: Long? = null, addedAt: Long = 0L) =
        LibraryBook(id, "T$id", null, null, progress, lastReadAt, addedAt)

    // HOM-002: escenario "el de hoy gana al de ayer"
    @Test
    fun continueReadingIsTheMostRecentlyReadBook() {
        val content = homeContent(listOf(book("ayer", 42, 100), book("hoy", 10, 200)))
        assertEquals("hoy", content.continueReading?.id)
    }

    // HOM-002: empate, gana el primero de la lista (el importado más reciente)
    @Test
    fun tieOnLastReadKeepsLibraryOrder() {
        val content = homeContent(listOf(book("a", 5, 100), book("b", 5, 100)))
        assertEquals("a", content.continueReading?.id)
    }

    // HOM-002: escenario "al 100 % ya no aparece"
    @Test
    fun finishedAndNewBooksAreNotContinueReading() {
        val content = homeContent(listOf(book("fin", 100, 300), book("nuevo", null)))
        assertNull(content.continueReading)
    }

    // HOM-003
    @Test
    fun emptyLibraryIsFlagged() {
        assertEquals(true, homeContent(emptyList()).libraryEmpty)
        assertEquals(false, homeContent(listOf(book("nuevo", null))).libraryEmpty)
    }

    // HOM-008: excluye el de Continuar leyendo, más reciente primero, con total
    @Test
    fun readingExcludesContinueReadingAndKeepsTotal() {
        val content = homeContent(listOf(book("a", 10, 1), book("b", 20, 3), book("c", 30, 2), book("nuevo", null)))
        assertEquals("b", content.continueReading?.id)
        assertEquals(listOf("c", "a"), content.reading.map { it.id })
        assertEquals(2, content.readingCount)
    }

    // HOM-008: con un solo libro en lectura la sección queda vacía
    @Test
    fun readingIsEmptyWithASingleBookInProgress() {
        val content = homeContent(listOf(book("a", 10, 1)))
        assertEquals(emptyList<LibraryBook>(), content.reading)
        assertEquals(0, content.readingCount)
    }

    // HOM-008 a HOM-010: máximo 5 por fila, el total cuenta todos
    @Test
    fun rowsAreLimitedToFiveButCountsAreTotals() {
        val books = (1..8).map { book("r$it", 10, it.toLong(), addedAt = it.toLong()) } +
            (1..7).map { book("f$it", 100, it.toLong(), addedAt = 100L + it) }
        val content = homeContent(books)
        assertEquals(5, content.reading.size)
        assertEquals(7, content.readingCount)
        assertEquals(5, content.finished.size)
        assertEquals(7, content.finishedCount)
        assertEquals(5, content.recentlyAdded.size)
        assertEquals(15, content.recentlyAddedCount)
    }

    // HOM-009: escenario "importo un libro, es el primero"
    @Test
    fun recentlyAddedIsNewestImportFirstAnyStatus() {
        val content = homeContent(listOf(book("viejo", 100, 9, addedAt = 1), book("nuevo", null, addedAt = 5), book("medio", 40, 2, addedAt = 3)))
        assertEquals(listOf("nuevo", "medio", "viejo"), content.recentlyAdded.map { it.id })
    }

    // HOM-010: solo terminados, el último leído primero
    @Test
    fun finishedHasOnlyFinishedBooksMostRecentFirst() {
        val content = homeContent(listOf(book("f1", 100, 1), book("leyendo", 42, 9), book("f2", 100, 5), book("nuevo", null)))
        assertEquals(listOf("f2", "f1"), content.finished.map { it.id })
    }

    // HOM-001
    @Test
    fun greetingFollowsHourOfDay() {
        assertEquals(Greeting.Night, greetingFor(4))
        assertEquals(Greeting.Morning, greetingFor(5))
        assertEquals(Greeting.Morning, greetingFor(11))
        assertEquals(Greeting.Afternoon, greetingFor(12))
        assertEquals(Greeting.Afternoon, greetingFor(19))
        assertEquals(Greeting.Night, greetingFor(20))
        assertEquals(Greeting.Night, greetingFor(0))
    }
}
