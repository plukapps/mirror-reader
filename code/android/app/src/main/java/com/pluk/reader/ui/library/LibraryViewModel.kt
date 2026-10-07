package com.pluk.reader.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pluk.reader.domain.model.LibraryFilter
import com.pluk.reader.domain.model.countBy
import com.pluk.reader.domain.model.filterBy
import com.pluk.reader.domain.repository.LibraryRepository
import com.pluk.reader.domain.usecase.ImportBooksUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class LibraryViewModel @Inject constructor(
    library: LibraryRepository,
    private val importBooks: ImportBooksUseCase,
) : ViewModel() {
    private val filter = MutableStateFlow(LibraryFilter.All)
    private val importing = MutableStateFlow(false)

    private val _messages = MutableSharedFlow<LibraryMessage>(extraBufferCapacity = MESSAGE_BUFFER)
    val messages: SharedFlow<LibraryMessage> = _messages.asSharedFlow()

    val uiState: StateFlow<LibraryUiState> = combine(library.books, filter, importing) { books, filter, importing ->
        LibraryUiState(
            loading = false,
            books = books.filterBy(filter),
            filter = filter,
            allCount = books.countBy(LibraryFilter.All),
            readingCount = books.countBy(LibraryFilter.Reading),
            finishedCount = books.countBy(LibraryFilter.Finished),
            importing = importing,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), LibraryUiState())

    fun onFilterSelected(selected: LibraryFilter) {
        filter.value = selected
    }

    /** LIB-001: importa los archivos elegidos y avisa el resultado. */
    fun onImport(uris: List<String>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            importing.value = true
            val outcomes = try {
                importBooks(uris)
            } finally {
                importing.value = false
            }
            outcomes.toMessages().forEach { _messages.tryEmit(it) }
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
        const val MESSAGE_BUFFER = 32
    }
}
