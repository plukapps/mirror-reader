package com.pluk.reader.library

import com.pluk.reader.domain.model.ImportOutcome
import com.pluk.reader.domain.model.LibraryBook
import com.pluk.reader.domain.model.LibraryFilter
import com.pluk.reader.domain.repository.LibraryRepository
import com.pluk.reader.domain.usecase.ImportBooksUseCase
import com.pluk.reader.ui.library.LibraryMessage
import com.pluk.reader.ui.library.LibraryUiState
import com.pluk.reader.ui.library.LibraryViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    private class FakeLibrary(initial: List<LibraryBook> = emptyList()) : LibraryRepository {
        val state = MutableStateFlow(initial)
        override val books = state
        val outcomes = mutableMapOf<String, ImportOutcome>()
        override suspend fun import(uri: String): ImportOutcome = outcomes.getValue(uri)
    }

    private fun book(id: String, progress: Int?) = LibraryBook(id, "T$id", null, null, progress)

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(library: FakeLibrary) = LibraryViewModel(library, ImportBooksUseCase(library))

    private suspend fun LibraryViewModel.ready(): LibraryUiState = uiState.first { !it.loading }

    // LIB-010, LIB-011
    @Test
    fun tabsShowCountsAndFilterTheGrid() = runTest(dispatcher) {
        val vm = viewModel(FakeLibrary(listOf(book("a", null), book("b", 42), book("c", 100))))
        val all = vm.ready()
        assertEquals(Triple(3, 1, 1), Triple(all.allCount, all.readingCount, all.finishedCount))
        assertEquals(3, all.books.size)

        vm.onFilterSelected(LibraryFilter.Reading)
        assertEquals(listOf("b"), vm.uiState.first { it.filter == LibraryFilter.Reading }.books.map { it.id })
        vm.onFilterSelected(LibraryFilter.Finished)
        assertEquals(listOf("c"), vm.uiState.first { it.filter == LibraryFilter.Finished }.books.map { it.id })
    }

    // LIB-011: el progreso se actualiza cuando cambia la posición guardada
    @Test
    fun gridFollowsProgressChanges() = runTest(dispatcher) {
        val library = FakeLibrary(listOf(book("a", 10)))
        val vm = viewModel(library)
        assertEquals(10, vm.ready().books.single().progressPercent)
        library.state.value = listOf(book("a", 55))
        assertEquals(55, vm.uiState.first { it.books.singleOrNull()?.progressPercent == 55 }.books.single().progressPercent)
    }

    @Test
    fun emptyLibraryIsReported() = runTest(dispatcher) {
        assertTrue(viewModel(FakeLibrary()).ready().isLibraryEmpty)
        assertFalse(viewModel(FakeLibrary(listOf(book("a", null)))).ready().isLibraryEmpty)
    }

    // LIB-001, LIB-002, LIB-003: avisos tras importar varios archivos
    @Test
    fun importReportsEachOutcome() = runTest(dispatcher) {
        val library = FakeLibrary()
        library.outcomes["a"] = ImportOutcome.Imported("1", "A")
        library.outcomes["b"] = ImportOutcome.Imported("2", "B")
        library.outcomes["c"] = ImportOutcome.AlreadyInLibrary("3", "C")
        library.outcomes["d"] = ImportOutcome.Rejected("dañado")
        val vm = viewModel(library)
        val received = mutableListOf<LibraryMessage>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.messages.collect { received += it } }
        advanceUntilIdle()

        vm.onImport(listOf("a", "b", "c", "d"))
        advanceUntilIdle()

        assertEquals(
            listOf(LibraryMessage.Imported(2), LibraryMessage.AlreadyInLibrary("C"), LibraryMessage.Rejected("dañado")),
            received,
        )
    }

    @Test
    fun importingFlagIsClearedAfterImport() = runTest(dispatcher) {
        val library = FakeLibrary()
        library.outcomes["a"] = ImportOutcome.Imported("1", "A")
        val vm = viewModel(library)
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()
        vm.onImport(listOf("a"))
        advanceUntilIdle()
        assertFalse(vm.uiState.value.importing)
    }

    @Test
    fun emptySelectionDoesNothing() = runTest(dispatcher) {
        val library = FakeLibrary()
        val vm = viewModel(library)
        val received = mutableListOf<LibraryMessage>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.messages.collect { received += it } }
        vm.onImport(emptyList())
        advanceUntilIdle()
        assertTrue(received.isEmpty())
    }
}
