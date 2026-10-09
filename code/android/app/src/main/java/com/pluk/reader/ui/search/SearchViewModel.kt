package com.pluk.reader.ui.search

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pluk.reader.domain.model.LibraryBook
import com.pluk.reader.domain.repository.LibraryRepository
import com.pluk.reader.domain.search.SearchScope
import com.pluk.reader.domain.search.searchBooks
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class SearchUiState(
    val loading: Boolean = true,
    val query: String = "",
    val scope: SearchScope = SearchScope.All,
    /** Libros que coinciden, en el orden de LIB-015. */
    val results: List<LibraryBook> = emptyList(),
) {
    /** Hay algo escrito: se muestran resultados (o que no hay) en lugar de la ayuda. */
    val hasQuery: Boolean get() = query.isNotBlank()
}

/** Búsqueda local en la biblioteca (LIB-006, LIB-013, LIB-015). */
@HiltViewModel
class SearchViewModel @Inject constructor(
    library: LibraryRepository,
    private val savedState: SavedStateHandle,
) : ViewModel() {
    /** Texto del campo. Se actualiza al instante, sin esperar los resultados, para no mover el cursor. */
    val query: StateFlow<String> = savedState.getStateFlow(KEY_QUERY, "")
    private val scope = savedState.getStateFlow(KEY_SCOPE, SearchScope.All.name)

    val uiState: StateFlow<SearchUiState> = combine(library.books, query, scope) { books, query, scopeName ->
        val scope = SearchScope.entries.firstOrNull { it.name == scopeName } ?: SearchScope.All
        SearchUiState(loading = false, query = query, scope = scope, results = searchBooks(books, query, scope))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), SearchUiState())

    fun onQueryChange(text: String) {
        savedState[KEY_QUERY] = text
    }

    fun onScopeChange(selected: SearchScope) {
        savedState[KEY_SCOPE] = selected.name
    }

    fun onClear() = onQueryChange("")

    private companion object {
        const val KEY_QUERY = "query"
        const val KEY_SCOPE = "scope"
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
