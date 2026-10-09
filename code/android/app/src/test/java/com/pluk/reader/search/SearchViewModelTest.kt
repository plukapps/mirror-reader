package com.pluk.reader.search

import androidx.lifecycle.SavedStateHandle
import com.pluk.reader.domain.model.ImportOutcome
import com.pluk.reader.domain.model.LibraryBook
import com.pluk.reader.domain.repository.LibraryRepository
import com.pluk.reader.domain.search.SearchScope
import com.pluk.reader.ui.search.SearchViewModel
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    private class FakeLibrary(initial: List<LibraryBook>) : LibraryRepository {
        val state = MutableStateFlow(initial)
        override val books = state
        override suspend fun import(uri: String): ImportOutcome = ImportOutcome.Imported("x", "X")
    }

    private val library = FakeLibrary(
        listOf(
            LibraryBook("let", "Cartas a Lucilio", "Séneca", null, 42),
            LibraryBook("abo", "Vida de Séneca", "Pierre Grimal", null, null),
        ),
    )

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    // LIB-006: sin consulta no hay resultados
    @Test
    fun startsWithoutQuery() = runTest(dispatcher) {
        val vm = SearchViewModel(library, SavedStateHandle())
        val state = vm.uiState.first { !it.loading }
        assertFalse(state.hasQuery)
        assertTrue(state.results.isEmpty())
    }

    // LIB-013: los resultados aparecen mientras escribo
    @Test
    fun typingShowsResults() = runTest(dispatcher) {
        val vm = SearchViewModel(library, SavedStateHandle())
        vm.onQueryChange("seneca")
        val state = vm.uiState.first { it.query == "seneca" && !it.loading }
        assertTrue(state.hasQuery)
        assertEquals(listOf("abo", "let"), state.results.map { it.id })
    }

    // LIB-015
    @Test
    fun authorsScopeFiltersResults() = runTest(dispatcher) {
        val vm = SearchViewModel(library, SavedStateHandle())
        vm.onQueryChange("seneca")
        vm.onScopeChange(SearchScope.Authors)
        val state = vm.uiState.first { it.scope == SearchScope.Authors && !it.loading }
        assertEquals(listOf("let"), state.results.map { it.id })
    }

    // LIB-014: el progreso del resultado sigue a la biblioteca (al volver del lector)
    @Test
    fun followsLibraryChanges() = runTest(dispatcher) {
        val vm = SearchViewModel(library, SavedStateHandle())
        vm.onQueryChange("cartas")
        vm.uiState.first { it.results.isNotEmpty() }
        library.state.value = listOf(LibraryBook("let", "Cartas a Lucilio", "Séneca", null, 80))
        val state = vm.uiState.first { it.results.firstOrNull()?.progressPercent == 80 }
        assertEquals(listOf("let"), state.results.map { it.id })
    }

    // La consulta y el filtro sobreviven a la recreación (rotación)
    @Test
    fun restoresQueryAndScope() = runTest(dispatcher) {
        val saved = SavedStateHandle()
        SearchViewModel(library, saved).apply {
            onQueryChange("seneca")
            onScopeChange(SearchScope.Authors)
        }
        val state = SearchViewModel(library, saved).uiState.first { !it.loading }
        assertEquals("seneca", state.query)
        assertEquals(SearchScope.Authors, state.scope)
        assertEquals(listOf("let"), state.results.map { it.id })
    }

    @Test
    fun clearEmptiesTheQuery() = runTest(dispatcher) {
        val vm = SearchViewModel(library, SavedStateHandle())
        vm.onQueryChange("seneca")
        vm.onClear()
        val state = vm.uiState.first { !it.loading && it.query.isEmpty() }
        assertTrue(state.results.isEmpty())
    }
}
