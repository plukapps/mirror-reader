package com.pluk.reader.ui.search

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pluk.reader.domain.model.LibraryBook
import com.pluk.reader.domain.repository.LibraryRepository
import com.pluk.reader.domain.store.Acquisition
import com.pluk.reader.domain.store.CatalogResult
import com.pluk.reader.domain.store.DeliveryOutcome
import com.pluk.reader.domain.store.SearchFilter
import com.pluk.reader.domain.store.StoreAcquisitions
import com.pluk.reader.domain.store.StoreBook
import com.pluk.reader.domain.store.StoreCatalog
import com.pluk.reader.domain.store.StoreDelivery
import com.pluk.reader.domain.store.StoreResult
import com.pluk.reader.domain.store.catalogResults
import com.pluk.reader.domain.store.libraryResults
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Estado de la búsqueda en la tienda (STO-001, STO-007). */
enum class StoreStatus { Idle, Loading, Ready, Unavailable }

/** Estado de obtener un libro de la tienda (STO-003). */
sealed interface StoreItemState {
    data object Downloading : StoreItemState

    data class Failed(val message: String) : StoreItemState

    /** Entregado, mientras la biblioteca todavía no lo muestra. */
    data class Delivered(val bookId: String) : StoreItemState
}

data class SearchUiState(
    val loading: Boolean = true,
    val query: String = "",
    val filter: SearchFilter = SearchFilter.All,
    /** Libros de la biblioteca que coinciden, en el orden de LIB-015. */
    val results: List<LibraryBook> = emptyList(),
    val storeStatus: StoreStatus = StoreStatus.Idle,
    /** Resultados del catálogo (STO-002), ya filtrados (STO-006). */
    val storeResults: List<StoreResult> = emptyList(),
    val itemStates: Map<String, StoreItemState> = emptyMap(),
    /** Compra simulada esperando confirmación (STO-004). */
    val pendingPurchase: StoreBook? = null,
) {
    /** Hay algo escrito: se muestran resultados (o que no hay) en lugar de la ayuda. */
    val hasQuery: Boolean get() = query.isNotBlank()
}

private data class CatalogState(val status: StoreStatus, val books: List<StoreBook> = emptyList())

/** Búsqueda en la biblioteca (LIB-006, LIB-013 a LIB-015) y en la tienda (STO-001 a STO-008). */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    library: LibraryRepository,
    private val catalog: StoreCatalog,
    private val delivery: StoreDelivery,
    private val acquisitions: StoreAcquisitions,
    private val savedState: SavedStateHandle,
) : ViewModel() {
    /** Texto del campo. Se actualiza al instante, sin esperar los resultados, para no mover el cursor. */
    val query: StateFlow<String> = savedState.getStateFlow(KEY_QUERY, "")
    private val filter = savedState.getStateFlow(KEY_FILTER, SearchFilter.All.name)
        .map { name -> SearchFilter.entries.firstOrNull { it.name == name } ?: SearchFilter.All }

    private val itemStates = MutableStateFlow<Map<String, StoreItemState>>(emptyMap())
    private val pendingPurchase = MutableStateFlow<StoreBook?>(null)

    // STO-001: espera una pausa al escribir; una consulta nueva cancela la anterior.
    private val catalogState = combine(query, filter) { query, filter -> query.trim() to (filter == SearchFilter.Authors) }
        .distinctUntilChanged()
        .flatMapLatest { (query, byAuthor) ->
            if (query.length < MIN_QUERY_LENGTH) {
                flowOf(CatalogState(StoreStatus.Idle))
            } else {
                flow {
                    emit(CatalogState(StoreStatus.Loading))
                    delay(DEBOUNCE_MS)
                    emit(
                        when (val result = catalog.search(query, byAuthor)) {
                            is CatalogResult.Found -> CatalogState(StoreStatus.Ready, result.books)
                            CatalogResult.Unavailable -> CatalogState(StoreStatus.Unavailable)
                        },
                    )
                }
            }
        }

    private val searchInputs = combine(library.books, query, filter, acquisitions.acquired) { books, query, filter, acquired ->
        SearchInputs(books, query, filter, acquired)
    }

    val uiState: StateFlow<SearchUiState> =
        combine(searchInputs, catalogState, itemStates, pendingPurchase) { inputs, catalog, items, purchase ->
            val store = catalogResults(catalog.books, inputs.filter, inputs.acquired, inputs.books)
            val inCatalog = store.mapNotNull { it.inLibrary?.id }.toSet()
            SearchUiState(
                loading = false,
                query = inputs.query,
                filter = inputs.filter,
                results = libraryResults(inputs.books, inputs.query, inputs.filter, exclude = inCatalog),
                // "En mi biblioteca" no muestra la tienda (STO-006).
                storeStatus = if (inputs.filter == SearchFilter.InLibrary) StoreStatus.Idle else catalog.status,
                storeResults = store,
                // Lo entregado deja de mostrarse como tal cuando la biblioteca ya lo tiene.
                itemStates = items.filterNot { (key, state) ->
                    state is StoreItemState.Delivered && store.any { it.book.key == key && it.inLibrary != null }
                },
                pendingPurchase = purchase,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), SearchUiState())

    fun onQueryChange(text: String) {
        savedState[KEY_QUERY] = text
    }

    fun onFilterChange(selected: SearchFilter) {
        savedState[KEY_FILTER] = selected.name
    }

    fun onClear() = onQueryChange("")

    /** "Obtener" un libro gratis (STO-003). */
    fun onGet(book: StoreBook) {
        val sources = (book.acquisition as? Acquisition.Free)?.sources ?: return
        deliver(book, sources)
    }

    /** "Comprar": pide confirmación (STO-004). */
    fun onBuy(book: StoreBook) {
        if (book.acquisition is Acquisition.Paid) pendingPurchase.value = book
    }

    fun onDismissPurchase() {
        pendingPurchase.value = null
    }

    /** Compra simulada: no se cobra nada y el libro se entrega como al obtenerlo (STO-004). */
    fun onConfirmPurchase() {
        val book = pendingPurchase.value ?: return
        pendingPurchase.value = null
        val sources = (book.acquisition as? Acquisition.Paid)?.sources ?: return
        deliver(book, sources)
    }

    private fun deliver(book: StoreBook, sources: List<String>) {
        if (itemStates.value[book.key] == StoreItemState.Downloading) return
        itemStates.update { it + (book.key to StoreItemState.Downloading) }
        viewModelScope.launch {
            val state = when (val outcome = delivery.deliver(sources)) {
                is DeliveryOutcome.Delivered -> {
                    acquisitions.record(book.key, outcome.bookId)
                    StoreItemState.Delivered(outcome.bookId)
                }
                is DeliveryOutcome.Failed -> StoreItemState.Failed(outcome.message)
            }
            itemStates.update { it + (book.key to state) }
        }
    }

    private data class SearchInputs(
        val books: List<LibraryBook>,
        val query: String,
        val filter: SearchFilter,
        val acquired: Map<String, String>,
    )

    private companion object {
        const val KEY_QUERY = "query"
        const val KEY_FILTER = "filter"
        const val STOP_TIMEOUT_MS = 5_000L
        const val DEBOUNCE_MS = 400L
        const val MIN_QUERY_LENGTH = 2
    }
}
