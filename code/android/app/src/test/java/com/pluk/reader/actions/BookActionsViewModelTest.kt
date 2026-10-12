package com.pluk.reader.actions

import com.pluk.reader.domain.model.LibraryBook
import com.pluk.reader.domain.model.UNREAD_LOCATOR
import com.pluk.reader.domain.repository.BookEditor
import com.pluk.reader.domain.repository.PositionRepository
import com.pluk.reader.domain.usecase.BookActions
import com.pluk.reader.ui.actions.BookAction
import com.pluk.reader.ui.actions.BookActionMessage
import com.pluk.reader.ui.actions.BookActionsViewModel
import com.pluk.reader.ui.actions.menuActions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BookActionsViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    private class FakePositions : PositionRepository {
        val saved = mutableMapOf<String, Pair<String, Double?>>()
        override suspend fun get(bookId: String): String? = saved[bookId]?.first
        override suspend fun save(bookId: String, locatorJson: String, totalProgression: Double?) {
            saved[bookId] = locatorJson to totalProgression
        }
    }

    private class FakeEditor : BookEditor {
        var coverOk = true
        val removed = mutableListOf<String>()
        override suspend fun replaceCover(bookId: String, imageUri: String) = coverOk
        override suspend fun remove(bookId: String) {
            removed += bookId
        }
    }

    private val positions = FakePositions()
    private val editor = FakeEditor()
    private val vm by lazy { BookActionsViewModel(BookActions(positions, editor) {}) }

    private fun book(id: String, progress: Int?) = LibraryBook(id, "Drácula", "Bram Stoker", null, progress)

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    // LIB-016, LIB-018: un libro terminado ofrece "no leído" en lugar de "terminado"
    @Test
    fun menuActionsDependOnStatus() {
        val full = listOf(BookAction.Read, BookAction.MarkFinished, BookAction.ChangeCover, BookAction.Remove)
        assertEquals(full, menuActions(book("n", null)))
        assertEquals(full, menuActions(book("l", 42)))
        assertEquals(
            listOf(BookAction.Read, BookAction.MarkUnread, BookAction.ChangeCover, BookAction.Remove),
            menuActions(book("t", 100)),
        )
    }

    // LIB-018
    @Test
    fun markFinishedSavesAndTells() = runTest(dispatcher) {
        val message = async { vm.messages.first() }
        vm.markFinished(book("b", 42))
        advanceUntilIdle()

        assertEquals(1.0, positions.saved["b"]?.second)
        assertEquals(BookActionMessage.MarkedFinished, message.await())
    }

    // LIB-018
    @Test
    fun markUnreadSavesAndTells() = runTest(dispatcher) {
        val message = async { vm.messages.first() }
        vm.markUnread(book("b", 100))
        advanceUntilIdle()

        assertEquals(UNREAD_LOCATOR to 0.0, positions.saved["b"])
        assertEquals(BookActionMessage.MarkedUnread, message.await())
    }

    // LIB-019: si la foto no sirve, se avisa
    @Test
    fun coverFailureIsReported() = runTest(dispatcher) {
        editor.coverOk = false
        val message = async { vm.messages.first() }
        vm.changeCover("b", "content://foto")
        advanceUntilIdle()

        assertEquals(BookActionMessage.CoverFailed, message.await())
    }

    // LIB-020
    @Test
    fun removeRemovesAndTellsTheTitle() = runTest(dispatcher) {
        val message = async { vm.messages.first() }
        vm.remove(book("b", 42))
        advanceUntilIdle()

        assertEquals(listOf("b"), editor.removed)
        assertEquals(BookActionMessage.Removed("Drácula"), message.await())
    }
}
