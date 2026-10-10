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
import com.pluk.reader.domain.store.Acquisition
import com.pluk.reader.domain.store.SearchFilter
import com.pluk.reader.domain.store.StoreBook
import com.pluk.reader.domain.store.StoreResult
import com.pluk.reader.domain.store.libraryResults
import com.pluk.reader.ui.search.SearchContent
import com.pluk.reader.ui.search.SearchUiState
import com.pluk.reader.ui.search.StoreItemState
import com.pluk.reader.ui.search.StoreStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
        var filter by remember { mutableStateOf(SearchFilter.All) }
        SearchContent(
            query = query,
            state = SearchUiState(loading = false, query = query, filter = filter, results = libraryResults(books, query, filter)),
            onQueryChange = { query = it },
            onClear = { query = "" },
            onFilterChange = { filter = it },
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
        compose.onNodeWithTag("filter-Authors").performClick()
        compose.onNodeWithTag("filter-Authors").assertIsSelected()
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
        compose.onNodeWithText("Buscá por título o autor en tu biblioteca y en la tienda.").assertIsDisplayed()
    }

    private val free = StoreBook("/works/F", "Frankenstein", listOf("Mary Shelley"), null, Acquisition.Free(listOf("f")))
    private val paid = StoreBook("/works/P", "Drácula", listOf("Bram Stoker"), null, Acquisition.Paid(listOf("p"), 499))
    private val unavailable = StoreBook("/works/U", "Libro nuevo", listOf("Autora"), null, Acquisition.Unavailable)

    private fun showStore(
        state: SearchUiState,
        onGet: (StoreBook) -> Unit = {},
        onBuy: (StoreBook) -> Unit = {},
        onConfirm: () -> Unit = {},
        onBook: (String) -> Unit = {},
    ) = compose.setContent {
        SearchContent(
            query = state.query,
            state = state,
            onQueryChange = {},
            onClear = {},
            onFilterChange = {},
            onBookClick = onBook,
            onGet = onGet,
            onBuy = onBuy,
            onConfirmPurchase = onConfirm,
        )
    }

    private fun storeState(vararg results: StoreResult, items: Map<String, StoreItemState> = emptyMap(), pending: StoreBook? = null) =
        SearchUiState(
            loading = false,
            query = "libro",
            storeStatus = StoreStatus.Ready,
            storeResults = results.toList(),
            itemStates = items,
            pendingPurchase = pending,
        )

    // STO-002: cada resultado dice cómo obtenerlo
    @Test
    fun catalogRowsShowHowToGetThem() {
        var got: StoreBook? = null
        var bought: StoreBook? = null
        showStore(storeState(StoreResult(free), StoreResult(paid), StoreResult(unavailable)), onGet = { got = it }, onBuy = { bought = it })
        compose.onNodeWithText("Gratis").assertIsDisplayed()
        compose.onNodeWithText("US$ 4,99 · precio de prueba").assertIsDisplayed()
        compose.onNodeWithText("No disponible en digital").assertIsDisplayed()
        compose.onNodeWithTag("store-get-/works/F").performClick()
        compose.onNodeWithTag("store-buy-/works/P").performClick()
        assertEquals(free, got)
        assertEquals(paid, bought)
    }

    // STO-003, STO-008: descargando y, al terminar, "En tu biblioteca" que abre el libro
    @Test
    fun downloadingAndDeliveredStates() {
        var opened: String? = null
        showStore(
            storeState(StoreResult(free), StoreResult(paid), items = mapOf(free.key to StoreItemState.Downloading, paid.key to StoreItemState.Delivered("hash-p"))),
            onBook = { opened = it },
        )
        compose.onNodeWithText("Descargando…").assertIsDisplayed()
        compose.onNodeWithText("En tu biblioteca").assertIsDisplayed()
        compose.onNodeWithTag("store-/works/P").performClick()
        assertEquals("hash-p", opened)
    }

    // STO-004: la hoja de compra dice que es simulada y confirma
    @Test
    fun purchaseSheetConfirms() {
        var confirmed = false
        showStore(storeState(StoreResult(paid), pending = paid), onConfirm = { confirmed = true })
        compose.onNodeWithTag("purchase-sheet").assertIsDisplayed()
        compose.onNodeWithText("Pagar US$ 4,99").assertIsDisplayed()
        compose.onNodeWithTag("purchase-confirm").performClick()
        assertTrue(confirmed)
    }

    // STO-007: sin conexión se avisa
    @Test
    fun unavailableStoreShowsNotice() {
        showStore(storeState().copy(storeStatus = StoreStatus.Unavailable))
        compose.onNodeWithText("La tienda no está disponible. Revisá la conexión.").assertIsDisplayed()
    }
}
