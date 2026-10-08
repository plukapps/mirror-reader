package com.pluk.reader.library

import com.pluk.reader.data.library.toLibraryBook
import com.pluk.reader.data.local.db.BookEntity
import com.pluk.reader.data.remote.toCloudOnlyEntity
import com.pluk.reader.data.remote.toRemoteBook
import com.pluk.reader.domain.model.LibraryBook
import com.pluk.reader.domain.remote.RemoteBook
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** LIB-007: un libro está en este dispositivo, solo en la nube, o ambos; y el mapeo con el modelo remoto. */
class RemoteBookMapperTest {
    private val local = BookEntity("a".repeat(64), "Moby Dick", "Herman Melville", hasCover = true, addedAt = 10, sizeBytes = 2048)

    // LIB-007
    @Test
    fun aLocalBookIsDownloadedAndNotUploadedYet() {
        val book = local.toLibraryBook(progression = null, coverPath = "/c.jpg")
        assertTrue(book.isDownloaded)
        assertFalse(book.isUploaded)
    }

    // LIB-007
    @Test
    fun anUploadedLocalBookIsBothDownloadedAndUploaded() {
        val book = local.copy(uploadedAt = 99).toLibraryBook(null, "/c.jpg")
        assertTrue(book.isDownloaded)
        assertTrue(book.isUploaded)
    }

    // LIB-007
    @Test
    fun aCloudOnlyBookIsNotDownloadedButUploaded() {
        val entity = RemoteBook(local.id, "Moby Dick", listOf("Herman Melville"), 2048).toCloudOnlyEntity(now = 50)
        val book = entity.toLibraryBook(null, "/no-existe.jpg")
        assertFalse(book.isDownloaded)
        assertTrue(book.isUploaded)
        assertNull("sin archivo no hay portada que mostrar", book.coverPath)
    }

    @Test
    fun existingConstructorsStillMeanDownloadedAndNotUploaded() {
        val book = LibraryBook("id", "T", null, null, null)
        assertTrue(book.isDownloaded)
        assertFalse(book.isUploaded)
    }

    @Test
    fun localBookMapsToRemoteMetadata() {
        assertEquals(RemoteBook(local.id, "Moby Dick", listOf("Herman Melville"), 2048), local.toRemoteBook(sizeBytes = 2048))
    }

    @Test
    fun noAuthorMapsToEmptyListAndBack() {
        for (blank in listOf(null, "", "  ")) {
            assertEquals(emptyList<String>(), local.copy(author = blank).toRemoteBook(1).authors)
        }
        assertNull(RemoteBook(local.id, "T", emptyList(), 1).toCloudOnlyEntity(1).author)
    }

    @Test
    fun severalRemoteAuthorsAreJoinedForDisplay() {
        val entity = RemoteBook(local.id, "T", listOf("Ana", "Beto"), 1).toCloudOnlyEntity(1)
        assertEquals("Ana, Beto", entity.author)
    }

    @Test
    fun theCombinedAuthorStringSurvivesALocalRoundTrip() {
        val combined = local.copy(author = "Ana, Beto")
        val back = combined.toRemoteBook(1).toCloudOnlyEntity(1)
        assertEquals("Ana, Beto", back.author)
    }

    @Test
    fun cloudOnlyEntityKeepsIdTitleSizeAndTimes() {
        val entity = RemoteBook(local.id, "Moby Dick", listOf("H"), 777).toCloudOnlyEntity(now = 123)
        assertEquals(local.id, entity.id)
        assertEquals("Moby Dick", entity.title)
        assertEquals(777L, entity.sizeBytes)
        assertFalse(entity.hasCover)
        assertFalse(entity.isDownloaded)
        assertEquals(123L, entity.addedAt)
        assertEquals(123L, entity.uploadedAt)
    }
}
