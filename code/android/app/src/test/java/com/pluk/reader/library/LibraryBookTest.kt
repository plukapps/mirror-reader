package com.pluk.reader.library

import com.pluk.reader.data.library.toLibraryBook
import com.pluk.reader.data.local.db.BookEntity
import com.pluk.reader.domain.model.LibraryBook
import com.pluk.reader.domain.model.LibraryFilter
import com.pluk.reader.domain.model.ReadingStatus
import com.pluk.reader.domain.model.countBy
import com.pluk.reader.domain.model.filterBy
import com.pluk.reader.domain.model.readingStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LibraryBookTest {
    private fun book(id: String, progress: Int?) = LibraryBook(id, "T$id", null, null, progress)

    private val books = listOf(
        book("nuevo", null),
        book("empezado", 0),
        book("a medias", 42),
        book("casi", 99),
        book("fin", 100),
    )

    // LIB-011
    @Test
    fun statusFollowsProgress() {
        assertEquals(ReadingStatus.New, readingStatus(null))
        assertEquals(ReadingStatus.Reading, readingStatus(0))
        assertEquals(ReadingStatus.Reading, readingStatus(99))
        assertEquals(ReadingStatus.Finished, readingStatus(100))
    }

    // LIB-010: escenario "libro al 42 %"
    @Test
    fun bookAt42PercentIsInReadingButNotInFinished() {
        assertEquals(true, books.filterBy(LibraryFilter.Reading).any { it.id == "a medias" })
        assertEquals(false, books.filterBy(LibraryFilter.Finished).any { it.id == "a medias" })
    }

    // LIB-010: un libro sin abrir no está en Leyendo ni en Terminados
    @Test
    fun unopenedBookOnlyAppearsInAll() {
        assertEquals(true, books.filterBy(LibraryFilter.All).any { it.id == "nuevo" })
        assertEquals(false, books.filterBy(LibraryFilter.Reading).any { it.id == "nuevo" })
        assertEquals(false, books.filterBy(LibraryFilter.Finished).any { it.id == "nuevo" })
    }

    // LIB-010: contadores de las pestañas
    @Test
    fun countsMatchEachTab() {
        assertEquals(5, books.countBy(LibraryFilter.All))
        assertEquals(3, books.countBy(LibraryFilter.Reading))
        assertEquals(1, books.countBy(LibraryFilter.Finished))
    }

    // LIB-011: la progresión guardada (0..1) se convierte en porcentaje
    @Test
    fun mapsProgressionToPercent() {
        val entity = BookEntity("h", "Título", "Autor", hasCover = true, addedAt = 1)
        assertEquals(42, entity.toLibraryBook(0.42, "/c.jpg").progressPercent)
        assertNull(entity.toLibraryBook(null, "/c.jpg").progressPercent)
    }

    // LIB-011: sin portada en el EPUB no se ofrece ruta
    @Test
    fun coverPathOnlyWhenBookHasCover() {
        val with = BookEntity("h", "T", null, hasCover = true, addedAt = 1)
        val without = with.copy(hasCover = false)
        assertEquals("/c.jpg", with.toLibraryBook(null, "/c.jpg").coverPath)
        assertNull(without.toLibraryBook(null, "/c.jpg").coverPath)
    }
}
