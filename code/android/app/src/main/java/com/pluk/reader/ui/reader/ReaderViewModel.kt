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
import com.pluk.reader.domain.model.RemotePosition
import com.pluk.reader.domain.progressPercent
import com.pluk.reader.domain.shouldOfferJump
import com.pluk.reader.domain.repository.BookRepository
import com.pluk.reader.domain.repository.PositionRepository
import com.pluk.reader.domain.repository.SettingsRepository
import com.pluk.reader.domain.usecase.DownloadBookUseCase
import com.pluk.reader.domain.usecase.OpeningPosition
import com.pluk.reader.domain.usecase.PositionSync
import com.pluk.reader.domain.usecase.ResolveOpeningPositionUseCase
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
import kotlinx.coroutines.flow.filter
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
    private val positionSync: PositionSync,
    private val resolveOpeningPosition: ResolveOpeningPositionUseCase,
) : ViewModel() {

    private data class PendingPosition(val locatorJson: String, val totalProgression: Double?)

    private sealed interface Phase {
        data object Loading : Phase
        data class Failed(val message: String) : Phase

        /** SYN-003: hay una lectura más reciente en otro dispositivo y la diferencia es grande. */
        data class Confirming(val choice: OpeningPosition.Confirm) : Phase
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

    /** SYN-013: lectura más adelantada de otro dispositivo que se ofrece seguir. Null si no hay. */
    private val remoteAhead = MutableStateFlow<RemotePosition?>(null)

    private val _jumps = MutableSharedFlow<String>(extraBufferCapacity = 1)

    /** SYN-013: locator al que la pantalla debe navegar porque el usuario eligió seguir desde otro dispositivo. */
    val jumps: SharedFlow<String> = _jumps.asSharedFlow()

    private data class Reading(val progress: Int?, val page: Int?, val chapter: String, val continueFrom: ContinueFrom?)

    private val position = combine(progress, pageNumber, chapterTitle, remoteAhead) { progress, page, chapter, remote ->
        Reading(
            progress, page, chapter,
            remote?.let { ContinueFrom(it.deviceName, progressPercent(it.position.progress)) },
        )
    }

    val uiState: StateFlow<ReaderUiState> = combine(
        phase, settingsRepository.settings, position, controlsVisible, settingsOpen,
    ) { phase, settings, position, controls, settingsOpen ->
        when (phase) {
            Phase.Loading -> ReaderUiState.Loading
            is Phase.Failed -> ReaderUiState.Failed(phase.message)
            is Phase.Confirming -> ReaderUiState.ResumePrompt(
                deviceName = phase.choice.remote.deviceName,
                remotePercent = progressPercent(phase.choice.remote.position.progress),
            )
            is Phase.Opened -> ReaderUiState.Ready(
                phase.book, phase.toc, settings, position.progress, position.page, controls, position.chapter, settingsOpen,
                position.continueFrom,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), ReaderUiState.Loading)

    init {
        open()
        savePositionWhileReading()
        offerPositionsFromOtherDevices()
    }

    // SYN-013: si otro dispositivo avanza este mismo libro mientras se lee, se ofrece seguir desde allí.
    private fun offerPositionsFromOtherDevices() {
        viewModelScope.launch {
            positionSync.remoteUpdates.filter { it.position.bookId == bookId }.collect { remote ->
                if (shouldOfferJump(pendingPosition.value?.totalProgression, remote)) remoteAhead.value = remote
            }
        }
    }

    /** SYN-013: el usuario aceptó seguir desde el otro dispositivo. La pantalla navega y la lectura nueva se guarda. */
    fun continueFromOtherDevice() {
        val remote = remoteAhead.value ?: return
        remoteAhead.value = null
        _jumps.tryEmit(remote.position.locatorJson)
    }

    /** SYN-013: el usuario descartó el aviso. */
    fun dismissContinueFrom() {
        remoteAhead.value = null
    }

    private fun open() {
        viewModelScope.launch {
            // LIB-007: un libro solo en la nube se descarga al abrirlo; si ya está en el dispositivo no hace nada.
            downloadBook(bookId).onFailure {
                phase.value = Phase.Failed(it.message ?: "No se pudo descargar el libro.")
                return@launch
            }
            // SYN-003, SYN-012: antes de mostrar el libro se consulta su posición más reciente en la nube.
            val resolved = resolveOpeningPosition(bookId)
            if (resolved is OpeningPosition.Confirm) {
                phase.value = Phase.Confirming(resolved)
                return@launch
            }
            openBook()
        }
    }

    /**
     * SYN-003: el usuario eligió. [useRemote]: continuar desde el otro dispositivo; si no, quedarse en la posición de
     * este (que pasa a ser la más reciente).
     */
    fun onResumeChoice(useRemote: Boolean) {
        val confirming = phase.value as? Phase.Confirming ?: return
        phase.value = Phase.Loading
        viewModelScope.launch {
            if (useRemote) resolveOpeningPosition.accept(confirming.choice.remote) else resolveOpeningPosition.decline(confirming.choice.local)
            openBook()
        }
    }

    private suspend fun openBook() {
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
        // SYN-013: el aviso se retira solo cuando la lectura de aquí alcanza a la del otro dispositivo.
        remoteAhead.value?.let { if (!shouldOfferJump(totalProgression, it)) remoteAhead.value = null }
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

    override fun onCleared() = close()

    /**
     * Cierra la lectura: envía la última posición y libera el navegador. Lo llama `onCleared` y, por no ser
     * accesible, también las pruebas.
     */
    fun close() {
        saveAndSendLastPosition()
        release()
    }

    /**
     * SYN-011: al cerrar el libro se guarda la última posición y se envía a la nube. Corre en el scope de la
     * aplicación porque `viewModelScope` ya se está cancelando.
     */
    private fun saveAndSendLastPosition() {
        val pending = pendingPosition.value ?: return
        val opened = phase.value as? Phase.Opened ?: return
        positionSync.onBookClosed { positions.save(opened.book.bookId, pending.locatorJson, pending.totalProgression) }
    }

    companion object {
        const val ARG_BOOK_ID = "bookId"
        private const val STOP_TIMEOUT_MS = 5_000L
        private const val SAVE_DEBOUNCE_MS = 250L
    }
}
