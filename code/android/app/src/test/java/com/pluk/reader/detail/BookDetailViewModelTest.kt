package com.pluk.reader.detail

import androidx.lifecycle.SavedStateHandle
import com.pluk.reader.domain.model.ImportOutcome
import com.pluk.reader.domain.model.LibraryBook
import com.pluk.reader.domain.repository.LibraryRepository
import com.pluk.reader.notes.FakeNotes
import com.pluk.reader.notes.note
import com.pluk.reader.ui.detail.BookDetailUiState
import com.pluk.reader.ui.detail.BookDetailViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BookDetailViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    private class FakeLibrary(initial: List<LibraryBook>) : LibraryRepository {
        val state = MutableStateFlow(initial)
        override val books = state
        override suspend fun import(uri: String): ImportOutcome = error("no se usa")
    }

    private val book = LibraryBook("hash-del-libro", "Meditaciones", "Marco Aurelio", null, 42)
    private val library = FakeLibrary(listOf(book, LibraryBook("otro-libro", "Otro", null, null, null)))
    private val notes = FakeNotes()

    private fun viewModel() =
        BookDetailViewModel(SavedStateHandle(mapOf(BookDetailViewModel.ARG_BOOK_ID to "hash-del-libro")), library, notes)

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    // LIB-022, ANN-010: el libro y sus notas, en el orden del libro; las de otros libros no aparecen
    @Test
    fun showsTheBookAndItsNotesInBookOrder() = runTest(dispatcher) {
        notes.all.value = listOf(note("tarde", progression = 0.8), note("ajena", bookId = "otro-libro"), note("temprano", progression = 0.2))
        val state = viewModel().uiState.first { it is BookDetailUiState.Ready } as BookDetailUiState.Ready
        assertEquals(book, state.book)
        assertEquals(listOf("temprano", "tarde"), state.notes.map { it.id })
    }

    // ANN-010: una nota nueva aparece sin volver a abrir la pantalla
    @Test
    fun followsNewNotes() = runTest(dispatcher) {
        val vm = viewModel()
        vm.uiState.first { it is BookDetailUiState.Ready && it.notes.isEmpty() }
        notes.save(note("nueva"))
        vm.uiState.first { it is BookDetailUiState.Ready && it.notes.map { n -> n.id } == listOf("nueva") }
    }

    // LIB-022: si el libro se quita de la biblioteca, la pantalla lo sabe y vuelve atrás
    @Test
    fun aRemovedBookIsGone() = runTest(dispatcher) {
        val vm = viewModel()
        vm.uiState.first { it is BookDetailUiState.Ready }
        library.state.value = library.state.value.filterNot { it.id == "hash-del-libro" }
        vm.uiState.first { it == BookDetailUiState.Gone }
    }
}
