package com.pluk.reader.ui.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pluk.reader.domain.model.LibraryBook
import com.pluk.reader.domain.model.Note
import com.pluk.reader.domain.repository.LibraryRepository
import com.pluk.reader.domain.repository.NoteRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

sealed interface BookDetailUiState {
    data object Loading : BookDetailUiState

    /** LIB-022: el libro y sus notas en el orden del libro (ANN-010). */
    data class Ready(val book: LibraryBook, val notes: List<Note>) : BookDetailUiState

    /** El libro ya no está en la biblioteca (se quitó, también desde otro dispositivo). */
    data object Gone : BookDetailUiState
}

/** Detalle de un libro (LIB-022). */
@HiltViewModel
class BookDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    library: LibraryRepository,
    notes: NoteRepository,
) : ViewModel() {
    val bookId: String = checkNotNull(savedStateHandle[ARG_BOOK_ID]) { "Falta el argumento $ARG_BOOK_ID" }

    val uiState: StateFlow<BookDetailUiState> = combine(library.books, notes.observe(bookId)) { books, bookNotes ->
        books.firstOrNull { it.id == bookId }
            ?.let { BookDetailUiState.Ready(it, bookNotes) }
            ?: BookDetailUiState.Gone
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), BookDetailUiState.Loading)

    companion object {
        const val ARG_BOOK_ID = "bookId"
        private const val STOP_TIMEOUT_MS = 5_000L
    }
}
