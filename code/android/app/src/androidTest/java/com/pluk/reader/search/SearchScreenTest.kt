package com.pluk.reader.search

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pluk.reader.domain.model.LibraryBook
import com.pluk.reader.domain.search.SearchScope
import com.pluk.reader.domain.search.searchBooks
import com.pluk.reader.ui.search.SearchContent
import com.pluk.reader.ui.search.SearchUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SearchScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val books = listOf(
        LibraryBook("let", "Cartas a Lucilio", "Séneca", null, 42),
        LibraryBook("abo", "Vida de Séneca", "Pierre Grimal", null, null),
        LibraryBook("wal", "Walden", "Thoreau", null, 100),
    )

    /** La pantalla con el mismo cálculo que hace el ViewModel, sin Hilt. */
    private fun show(onBook: (String) -> Unit = {}) = compose.setContent {
        var query by remember { mutableStateOf("") }
        var scope by remember { mutableStateOf(SearchScope.All) }
        SearchContent(
            query = query,
            state = SearchUiState(loading = false, query = query, scope = scope, results = searchBooks(books, query, scope)),
            onQueryChange = { query = it },
            onClear = { query = "" },
            onScopeChange = { scope = it },
            onBookClick = onBook,
        )
    }

    // LIB-013, LIB-014: escribir muestra los resultados, la cantidad y el estado
    @Test
    fun typingShowsResultsWithCountAndStatus() {
        show()
        compose.onNodeWithTag("search-field").performTextInput("seneca")
        compose.onNodeWithTag("search-count").assertIsDisplayed()
        compose.onNodeWithText("2 RESULTADOS").assertIsDisplayed()
        compose.onNodeWithTag("result-let").assertIsDisplayed()
        compose.onNodeWithTag("result-abo").assertIsDisplayed()
        compose.onNodeWithText("Leyendo · 42 %").assertIsDisplayed()
        compose.onNodeWithTag("result-wal").assertDoesNotExist()
    }

    // LIB-015: "Autores" deja solo los libros cuyo autor coincide
    @Test
    fun authorsScopeFilters() {
        show()
        compose.onNodeWithTag("search-field").performTextInput("seneca")
        compose.onNodeWithTag("scope-Authors").performClick()
        compose.onNodeWithTag("scope-Authors").assertIsSelected()
        compose.onNodeWithTag("result-let").assertIsDisplayed()
        compose.onNodeWithTag("result-abo").assertDoesNotExist()
    }

    // LIB-014: tocar un resultado lo abre
    @Test
    fun tappingAResultOpensIt() {
        var opened: String? = null
        show(onBook = { opened = it })
        compose.onNodeWithTag("search-field").performTextInput("walden")
        compose.onNodeWithTag("result-wal").performClick()
        assertEquals("wal", opened)
    }

    @Test
    fun noMatchesShowsTheMessageAndClearResets() {
        show()
        compose.onNodeWithTag("search-field").performTextInput("zzz")
        compose.onNodeWithTag("search-no-results").assertIsDisplayed()
        compose.onNodeWithTag("search-clear").performClick()
        compose.onNodeWithText("Buscá en tu biblioteca por título o autor.").assertIsDisplayed()
    }
}
