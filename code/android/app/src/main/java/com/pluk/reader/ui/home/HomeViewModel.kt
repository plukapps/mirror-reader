package com.pluk.reader.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pluk.reader.domain.home.HomeContent
import com.pluk.reader.domain.home.homeContent
import com.pluk.reader.domain.repository.LibraryRepository
import com.pluk.reader.domain.usecase.ImportBooksUseCase
import com.pluk.reader.ui.library.LibraryMessage
import com.pluk.reader.ui.library.toMessages
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HomeUiState(
    val loading: Boolean = true,
    val content: HomeContent = HomeContent(continueReading = null, forYou = emptyList(), libraryEmpty = true),
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    library: LibraryRepository,
    private val importBooks: ImportBooksUseCase,
) : ViewModel() {
    private val _messages = MutableSharedFlow<LibraryMessage>(extraBufferCapacity = MESSAGE_BUFFER)
    val messages: SharedFlow<LibraryMessage> = _messages.asSharedFlow()

    val uiState: StateFlow<HomeUiState> = library.books
        .map { HomeUiState(loading = false, content = homeContent(it)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), HomeUiState())

    /** HOM-003 con LIB-001: importa desde Inicio cuando la biblioteca está vacía. */
    fun onImport(uris: List<String>) {
        if (uris.isEmpty()) return
        viewModelScope.launch { importBooks(uris).toMessages().forEach { _messages.tryEmit(it) } }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
        const val MESSAGE_BUFFER = 32
    }
}
