package com.pluk.reader.home

import com.pluk.reader.domain.home.Greeting
import com.pluk.reader.domain.home.greetingFor
import com.pluk.reader.domain.home.homeContent
import com.pluk.reader.domain.model.LibraryBook
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HomeContentTest {
    private fun book(id: String, progress: Int?, lastReadAt: Long? = null) =
        LibraryBook(id, "T$id", null, null, progress, lastReadAt)

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

    // HOM-004
    @Test
    fun forYouHasOnlyNewBooksInLibraryOrder() {
        val content = homeContent(
            listOf(book("n1", null), book("leyendo", 42, 1), book("n2", null), book("fin", 100, 2)),
        )
        assertEquals(listOf("n1", "n2"), content.forYou.map { it.id })
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
