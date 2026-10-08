package com.pluk.reader.ui.reader

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pluk.reader.domain.BodyEndDetector
import com.pluk.reader.domain.BodyEntry
import com.pluk.reader.domain.ChapterEntry
import com.pluk.reader.domain.currentChapterTitle
import com.pluk.reader.domain.backMatterStart
import com.pluk.reader.domain.model.LineSpacing
import com.pluk.reader.domain.model.OpenedBook
import com.pluk.reader.domain.model.ReaderFont
import com.pluk.reader.domain.model.ReaderSettings
import com.pluk.reader.domain.model.ReadingTheme
import com.pluk.reader.domain.progressPercent
import com.pluk.reader.domain.repository.BookRepository
import com.pluk.reader.domain.repository.PositionRepository
import com.pluk.reader.domain.repository.SettingsRepository
import com.pluk.reader.domain.usecase.DownloadBookUseCase
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
    private val downloadBook: DownloadBookUseCase,
) : ViewModel() {

    private data class PendingPosition(val locatorJson: String, val totalProgression: Double?)

    private sealed interface Phase {
        data object Loading : Phase
        data class Failed(val message: String) : Phase
        data class Opened(val book: OpenedBook, val toc: List<TocEntry>) : Phase
    }

    private val bookId: String = checkNotNull(savedStateHandle[ARG_BOOK_ID]) { "Falta el argumento $ARG_BOOK_ID" }
    private val phase = MutableStateFlow<Phase>(Phase.Loading)
    private val progress = MutableStateFlow<Int?>(null)
    private val pageNumber = MutableStateFlow<Int?>(null)
    private val chapterTitle = MutableStateFlow("")
    private val controlsVisible = MutableStateFlow(true)
    private val settingsOpen = MutableStateFlow(false)
    private val pendingPosition = MutableStateFlow<PendingPosition?>(null)

    // RDR-012: se arma al abrir el libro.
    private var bodyEnd: BodyEndDetector? = null

    // RDR-013: se arman al abrir el libro.
    private var chapters: List<ChapterEntry> = emptyList()
    private var readingOrder: List<String> = emptyList()

    private val _events = MutableSharedFlow<ReaderEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<ReaderEvent> = _events.asSharedFlow()

    private val position = combine(progress, pageNumber, chapterTitle) { progress, page, chapter ->
        Triple(progress, page, chapter)
    }

    val uiState: StateFlow<ReaderUiState> = combine(
        phase, settingsRepository.settings, position, controlsVisible, settingsOpen,
    ) { phase, settings, position, controls, settingsOpen ->
        when (phase) {
            Phase.Loading -> ReaderUiState.Loading
            is Phase.Failed -> ReaderUiState.Failed(phase.message)
            is Phase.Opened -> ReaderUiState.Ready(
                phase.book, phase.toc, settings, position.first, position.second, controls, position.third, settingsOpen,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), ReaderUiState.Loading)

    init {
        open()
        savePositionWhileReading()
    }

    private fun open() {
        viewModelScope.launch {
            // LIB-007: un libro solo en la nube se descarga al abrirlo; si ya está en el dispositivo no hace nada.
            downloadBook(bookId).onFailure {
                phase.value = Phase.Failed(it.message ?: "No se pudo descargar el libro.")
                return@launch
            }
            books.open(bookId).fold(
                onSuccess = { book ->
                    val settings: ReaderSettings = settingsRepository.settings.first()
                    navigatorHost.install(book, settings) { url ->
                        _events.tryEmit(ReaderEvent.OpenExternalLink(url))
                    }
                    val readingOrder = book.publication.readingOrder.map { it.href.toString() }
                    this@ReaderViewModel.readingOrder = readingOrder
                    bodyEnd = BodyEndDetector(
                        readingOrder,
                        backMatterStart(
                            book.publication.tableOfContents.map { BodyEntry(it.title.orEmpty(), it.href.toString()) },
                            readingOrder,
                        ),
                    )
                    val toc = flattenToc(book.publication.tableOfContents)
                    chapters = toc.map { ChapterEntry(it.title, it.link.href.toString()) }
                    phase.value = Phase.Opened(book, toc)
                },
                onFailure = { phase.value = Phase.Failed(it.message ?: "No se pudo abrir el libro.") },
            )
        }
    }

    // RDR-006: se guarda la última posición, sin escribir en cada evento de scroll.
    @OptIn(FlowPreview::class)
    private fun savePositionWhileReading() {
        viewModelScope.launch {
            pendingPosition.filterNotNull().debounce(SAVE_DEBOUNCE_MS).collect { pending ->
                (phase.value as? Phase.Opened)?.let {
                    positions.save(it.book.bookId, pending.locatorJson, pending.totalProgression)
                }
            }
        }
    }

    /** RDR-005, RDR-006 y RDR-010: la pantalla informa cada cambio de posición con el locator serializado. */
    fun onLocatorChanged(locatorJson: String, totalProgression: Double?, position: Int? = null, href: String? = null) {
        progress.value = progressPercent(totalProgression)
        pageNumber.value = position
        pendingPosition.value = PendingPosition(locatorJson, totalProgression)
        navigatorHost.onLocatorChanged(locatorJson)
        if (href != null) chapterTitle.value = currentChapterTitle(chapters, readingOrder, href)
        if (href != null && bodyEnd?.onResource(href) == true) _events.tryEmit(ReaderEvent.BodyEnded)
    }

    fun setTheme(theme: ReadingTheme) = updateSettings { it.withTheme(theme) }
    fun setFont(font: ReaderFont) = updateSettings { it.withFont(font) }
    fun setLineSpacing(spacing: LineSpacing) = updateSettings { it.withLineSpacing(spacing) }
    fun biggerFont() = updateSettings { it.biggerFont() }
    fun smallerFont() = updateSettings { it.smallerFont() }
    fun togglePageAnimation() = updateSettings { it.togglePageAnimation() }
    fun toggleControls() {
        controlsVisible.value = !controlsVisible.value
    }

    /** RDR-014: "Aa". Abrir el panel también muestra los controles, para que la barra y el panel vayan juntos. */
    fun toggleSettings() {
        val open = !settingsOpen.value
        if (open) controlsVisible.value = true
        settingsOpen.value = open
    }

    fun closeSettings() {
        settingsOpen.value = false
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
        const val ARG_BOOK_ID = "bookId"
        private const val STOP_TIMEOUT_MS = 5_000L
        private const val SAVE_DEBOUNCE_MS = 250L
    }
}
