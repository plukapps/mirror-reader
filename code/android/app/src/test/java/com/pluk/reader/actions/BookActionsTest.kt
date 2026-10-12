package com.pluk.reader.actions

import com.pluk.reader.domain.model.UNREAD_LOCATOR
import com.pluk.reader.domain.repository.BookEditor
import com.pluk.reader.domain.repository.PositionRepository
import com.pluk.reader.domain.usecase.BookActions
import com.pluk.reader.domain.usecase.PositionFlushRequester
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BookActionsTest {
    private class FakePositions(val saved: MutableMap<String, Pair<String, Double?>> = mutableMapOf()) : PositionRepository {
        override suspend fun get(bookId: String): String? = saved[bookId]?.first
        override suspend fun save(bookId: String, locatorJson: String, totalProgression: Double?) {
            saved[bookId] = locatorJson to totalProgression
        }
    }

    private class FakeEditor(var coverOk: Boolean = true) : BookEditor {
        val removed = mutableListOf<String>()
        val covers = mutableListOf<Pair<String, String>>()
        override suspend fun replaceCover(bookId: String, imageUri: String): Boolean {
            covers += bookId to imageUri
            return coverOk
        }
        override suspend fun remove(bookId: String) {
            removed += bookId
        }
    }

    private class CountingFlusher : PositionFlushRequester {
        var requests = 0
        override fun requestFlush() {
            requests++
        }
    }

    private val positions = FakePositions()
    private val editor = FakeEditor()
    private val flusher = CountingFlusher()
    private var pushes = 0
    private val actions = BookActions(positions, editor, flusher) { pushes++ }

    // LIB-018: terminado conserva la última posición y queda al 100 %
    @Test
    fun markFinishedKeepsLocatorAndSetsFullProgress() = runTest {
        positions.saved["b"] = """{"href":"c3.xhtml"}""" to 0.42

        actions.markFinished("b")

        assertEquals("""{"href":"c3.xhtml"}""" to 1.0, positions.saved["b"])
        assertEquals(1, flusher.requests)
    }

    // LIB-018: un libro nunca abierto se marca terminado sin posición real
    @Test
    fun markFinishedWithoutPositionUsesUnreadLocator() = runTest {
        actions.markFinished("b")

        assertEquals(UNREAD_LOCATOR to 1.0, positions.saved["b"])
    }

    // LIB-018: no leído vuelve al principio y viaja como posición
    @Test
    fun markUnreadSavesUnreadLocatorAtZero() = runTest {
        positions.saved["b"] = """{"href":"c9.xhtml"}""" to 1.0

        actions.markUnread("b")

        assertEquals(UNREAD_LOCATOR to 0.0, positions.saved["b"])
        assertEquals(1, flusher.requests)
    }

    // LIB-019
    @Test
    fun setCoverDelegatesAndReportsResult() = runTest {
        assertTrue(actions.setCover("b", "content://foto"))
        editor.coverOk = false
        assertFalse(actions.setCover("b", "content://otra"))
        assertEquals(listOf("b" to "content://foto", "b" to "content://otra"), editor.covers)
    }

    // LIB-020
    @Test
    fun removeDelegatesToEditor() = runTest {
        actions.remove("b")

        assertEquals(listOf("b"), editor.removed)
        assertEquals(0, flusher.requests)
        // LIB-020: se borra de la cuenta en cuanto se pueda
        assertEquals(1, pushes)
    }
}
