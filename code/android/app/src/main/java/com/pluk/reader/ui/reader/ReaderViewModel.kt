package com.pluk.reader.ui.reader

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pluk.reader.domain.model.OpenedBook
import com.pluk.reader.domain.model.ReaderSettings
import com.pluk.reader.domain.progressPercent
import com.pluk.reader.domain.repository.BookRepository
import com.pluk.reader.domain.repository.PositionRepository
import com.pluk.reader.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ReaderViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val books: BookRepository,
    private val settingsRepository: SettingsRepository,
    private val positions: PositionRepository,
    private val navigatorHost: NavigatorHost,
) : ViewModel() {

    private sealed interface Phase {
        data object Loading : Phase
        data class Failed(val message: String) : Phase
        data class Opened(val book: OpenedBook, val toc: List<TocEntry>) : Phase
    }

    private val uri: String = checkNotNull(savedStateHandle[ARG_URI]) { "Falta el argumento $ARG_URI" }
    private val phase = MutableStateFlow<Phase>(Phase.Loading)
    private val progress = MutableStateFlow<Int?>(null)
    private val controlsVisible = MutableStateFlow(true)
    private val pendingPosition = MutableStateFlow<String?>(null)

    private val _events = MutableSharedFlow<ReaderEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<ReaderEvent> = _events.asSharedFlow()

    val uiState: StateFlow<ReaderUiState> = combine(
        phase, settingsRepository.settings, progress, controlsVisible,
    ) { phase, settings, progress, controls ->
        when (phase) {
            Phase.Loading -> ReaderUiState.Loading
            is Phase.Failed -> ReaderUiState.Failed(phase.message)
            is Phase.Opened -> ReaderUiState.Ready(phase.book, phase.toc, settings, progress, controls)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), ReaderUiState.Loading)

    init {
        open()
        savePositionWhileReading()
    }

    private fun open() {
        viewModelScope.launch {
            books.open(uri).fold(
                onSuccess = { book ->
                    val settings: ReaderSettings = settingsRepository.settings.first()
                    navigatorHost.install(book, settings) { url ->
                        _events.tryEmit(ReaderEvent.OpenExternalLink(url))
                    }
                    phase.value = Phase.Opened(book, flattenToc(book.publication.tableOfContents))
                },
                onFailure = { phase.value = Phase.Failed(it.message ?: "No se pudo abrir el libro.") },
            )
        }
    }

    // RDR-006: se guarda la última posición, sin escribir en cada evento de scroll.
    @OptIn(FlowPreview::class)
    private fun savePositionWhileReading() {
        viewModelScope.launch {
            pendingPosition.filterNotNull().debounce(SAVE_DEBOUNCE_MS).collect { json ->
                (phase.value as? Phase.Opened)?.let { positions.save(it.book.bookId, json) }
            }
        }
    }

    /** RDR-005 y RDR-006: la pantalla informa cada cambio de posición con el locator serializado. */
    fun onLocatorChanged(locatorJson: String, totalProgression: Double?) {
        progress.value = progressPercent(totalProgression)
        pendingPosition.value = locatorJson
    }

    fun toggleScroll() = updateSettings { it.toggleScroll() }
    fun nextTheme() = updateSettings { it.nextTheme() }
    fun biggerFont() = updateSettings { it.biggerFont() }
    fun smallerFont() = updateSettings { it.smallerFont() }
    fun togglePageAnimation() = updateSettings { it.togglePageAnimation() }
    fun toggleControls() {
        controlsVisible.value = !controlsVisible.value
    }

    fun hideControls() {
        controlsVisible.value = false
    }

    private fun updateSettings(transform: (ReaderSettings) -> ReaderSettings) {
        viewModelScope.launch { settingsRepository.update(transform) }
    }

    /** Libera el navegador. Lo llama `onCleared` y, por no ser accesible, también las pruebas. */
    fun release() = navigatorHost.clear()

    override fun onCleared() = release()

    companion object {
        const val ARG_URI = "uri"
        private const val STOP_TIMEOUT_MS = 5_000L
        private const val SAVE_DEBOUNCE_MS = 250L
    }
}
