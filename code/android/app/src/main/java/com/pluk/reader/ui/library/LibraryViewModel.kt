package com.pluk.reader.ui.library

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pluk.reader.domain.model.ImportOutcome
import com.pluk.reader.domain.model.LibraryFilter
import com.pluk.reader.domain.model.countBy
import com.pluk.reader.domain.model.filterBy
import com.pluk.reader.domain.repository.LibraryRepository
import com.pluk.reader.domain.account.AccountRepository
import com.pluk.reader.domain.usecase.ImportBooksUseCase
import com.pluk.reader.domain.plans.ImportQuotaCheck
import com.pluk.reader.domain.plans.UsageNoticePreference
import com.pluk.reader.domain.plans.shouldShowUsageNotice
import com.pluk.reader.domain.profile.QuotaCache
import com.pluk.reader.domain.profile.storageUsage
import com.pluk.reader.domain.usecase.LibrarySync
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
    private val librarySync: LibrarySync,
    account: AccountRepository,
    savedState: SavedStateHandle,
    private val importQuotaCheck: ImportQuotaCheck,
    quotaCache: QuotaCache,
    private val usageNoticePreference: UsageNoticePreference,
) : ViewModel() {
    /** HOM-011: Inicio abre la biblioteca con el filtro de la sección elegida. */
    private val filter = MutableStateFlow(
        LibraryFilter.entries.firstOrNull { it.name == savedState.get<String>(ARG_FILTER) } ?: LibraryFilter.All,
    )
    private val importing = MutableStateFlow(false)

    private val _messages = MutableSharedFlow<LibraryMessage>(extraBufferCapacity = MESSAGE_BUFFER)
    val messages: SharedFlow<LibraryMessage> = _messages.asSharedFlow()

    /** PLN-011: uso a mostrar en el aviso del 80 %, o null si no corresponde. GST-006: solo con sesión. */
    private val usageNotice = combine(quotaCache.quota, usageNoticePreference.dismissed) { quota, dismissed ->
        quota?.takeIf { shouldShowUsageNotice(it, dismissed) }?.let(::storageUsage)
    }

    val uiState: StateFlow<LibraryUiState> = combine(
        library.books,
        filter,
        importing,
        librarySync.state,
        account.user,
    ) { books, filter, importing, sync, user ->
        LibraryUiState(
            loading = false,
            books = books.filterBy(filter),
            filter = filter,
            allCount = books.countBy(LibraryFilter.All),
            readingCount = books.countBy(LibraryFilter.Reading),
            finishedCount = books.countBy(LibraryFilter.Finished),
            importing = importing,
            sync = sync,
            pendingUploadCount = books.count { it.isDownloaded && !it.isUploaded },
            signedIn = user != null,
        )
    }.combine(usageNotice) { state, notice -> state.copy(usageNotice = notice.takeIf { state.signedIn }) }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), LibraryUiState())

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
            // PLN-007 a PLN-009: si lo importado no entra en la nube, la hoja de planes (antes de la pasada).
            importQuotaCheck(outcomes)
            // SYN-001: lo importado se sube solo.
            if (outcomes.any { it is ImportOutcome.Imported }) librarySync.request()
        }
    }

    /** PLN-011: cerrar el aviso o tocar "Ver planes" lo descarta para siempre en esta cuenta y este dispositivo. */
    fun onDismissUsageNotice() {
        viewModelScope.launch { usageNoticePreference.dismiss() }
    }

    /** SYN-008: reintento manual tras un problema de sincronización. */
    fun onRetrySync() = librarySync.request()

    companion object {
        const val ARG_FILTER = "filter"
        private const val STOP_TIMEOUT_MS = 5_000L
        private const val MESSAGE_BUFFER = 32
    }
}
