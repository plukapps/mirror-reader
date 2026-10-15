package com.pluk.reader.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pluk.reader.domain.account.GuestNotice
import com.pluk.reader.domain.home.HomeContent
import com.pluk.reader.domain.home.homeContent
import com.pluk.reader.domain.repository.LibraryRepository
import com.pluk.reader.domain.usecase.ImportBooksUseCase
import com.pluk.reader.domain.usecase.LibrarySyncRequester
import com.pluk.reader.domain.model.ImportOutcome
import com.pluk.reader.domain.plans.ImportQuotaCheck
import com.pluk.reader.ui.library.LibraryMessage
import com.pluk.reader.ui.library.toMessages
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HomeUiState(
    val loading: Boolean = true,
    val content: HomeContent = HomeContent.Empty,
    /** GST-010: aviso "solo en este dispositivo", debajo del saludo. */
    val guestNotice: Boolean = false,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    library: LibraryRepository,
    private val importBooks: ImportBooksUseCase,
    private val librarySync: LibrarySyncRequester,
    private val importQuotaCheck: ImportQuotaCheck,
    private val guestNotice: GuestNotice,
) : ViewModel() {
    private val _messages = MutableSharedFlow<LibraryMessage>(extraBufferCapacity = MESSAGE_BUFFER)
    val messages: SharedFlow<LibraryMessage> = _messages.asSharedFlow()

    val uiState: StateFlow<HomeUiState> = combine(library.books, guestNotice.visible) { books, guest ->
        HomeUiState(loading = false, content = homeContent(books), guestNotice = guest)
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), HomeUiState())

    /** HOM-003 con LIB-001: importa desde Inicio cuando la biblioteca está vacía. */
    fun onImport(uris: List<String>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            val outcomes = importBooks(uris)
            outcomes.toMessages().forEach { _messages.tryEmit(it) }
            // PLN-007 a PLN-009: si lo importado no entra en la nube, la hoja de planes.
            importQuotaCheck(outcomes)
            // SYN-001: lo importado se sube solo, igual que desde la Biblioteca.
            if (outcomes.any { it is ImportOutcome.Imported }) librarySync.request()
        }
    }

    /** GST-010: cerrar el aviso o tocar "Crear cuenta" lo descarta en Inicio y en la Biblioteca. */
    fun onDismissGuestNotice() {
        viewModelScope.launch { guestNotice.dismiss() }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
        const val MESSAGE_BUFFER = 32
    }
}
