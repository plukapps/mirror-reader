package com.pluk.reader.ui.library

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pluk.reader.domain.model.LibraryFilter
import com.pluk.reader.domain.model.countBy
import com.pluk.reader.domain.model.filterBy
import com.pluk.reader.domain.repository.LibraryRepository
import com.pluk.reader.domain.account.AccountRepository
import com.pluk.reader.domain.usecase.ImportBooksUseCase
import com.pluk.reader.domain.usecase.UploadBooksUseCase
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
    private val uploadBooks: UploadBooksUseCase,
    account: AccountRepository,
    savedState: SavedStateHandle,
) : ViewModel() {
    /** HOM-011: Inicio abre la biblioteca con el filtro de la sección elegida. */
    private val filter = MutableStateFlow(
        LibraryFilter.entries.firstOrNull { it.name == savedState.get<String>(ARG_FILTER) } ?: LibraryFilter.All,
    )
    private val importing = MutableStateFlow(false)
    private val uploading = MutableStateFlow(false)

    private val _messages = MutableSharedFlow<LibraryMessage>(extraBufferCapacity = MESSAGE_BUFFER)
    val messages: SharedFlow<LibraryMessage> = _messages.asSharedFlow()

    val uiState: StateFlow<LibraryUiState> = combine(
        library.books,
        filter,
        importing,
        uploading,
        account.user,
    ) { books, filter, importing, uploading, user ->
        LibraryUiState(
            loading = false,
            books = books.filterBy(filter),
            filter = filter,
            allCount = books.countBy(LibraryFilter.All),
            readingCount = books.countBy(LibraryFilter.Reading),
            finishedCount = books.countBy(LibraryFilter.Finished),
            importing = importing,
            uploading = uploading,
            pendingUploadCount = books.count { it.isDownloaded && !it.isUploaded },
            signedIn = user != null,
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

    /** Sube los libros importados que aún no están en la nube y avisa el resultado (LIB-007, LIB-009, SYN-008). */
    fun onUpload() {
        // Un segundo toque durante la subida no lanza otra tanda.
        if (!uploading.compareAndSet(expect = false, update = true)) return
        viewModelScope.launch {
            val report = try {
                uploadBooks()
            } finally {
                uploading.value = false
            }
            report.toMessages().forEach { _messages.tryEmit(it) }
        }
    }

    companion object {
        const val ARG_FILTER = "filter"
        private const val STOP_TIMEOUT_MS = 5_000L
        private const val MESSAGE_BUFFER = 32
    }
}
