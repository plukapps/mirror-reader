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
import com.pluk.reader.domain.model.Note
import com.pluk.reader.domain.model.OpenedBook
import com.pluk.reader.domain.model.ReaderFont
import com.pluk.reader.domain.model.ReaderSettings
import com.pluk.reader.domain.model.ReadingTheme
import com.pluk.reader.domain.model.RemotePosition
import com.pluk.reader.domain.progressPercent
import com.pluk.reader.domain.shouldOfferJump
import com.pluk.reader.domain.repository.BookRepository
import com.pluk.reader.domain.repository.NoteRepository
import com.pluk.reader.domain.repository.PositionRepository
import com.pluk.reader.domain.repository.SettingsRepository
import com.pluk.reader.domain.usecase.DownloadBookUseCase
import com.pluk.reader.domain.usecase.OpeningPosition
import com.pluk.reader.domain.usecase.PositionSync
import com.pluk.reader.domain.usecase.ReadingSession
import com.pluk.reader.domain.usecase.ResolveOpeningPositionUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject
import com.pluk.reader.domain.plans.PlanCatalog
import com.pluk.reader.domain.plans.PlanId
import com.pluk.reader.domain.plans.PlanPrompts
import com.pluk.reader.domain.plans.allowReadingOption
import com.pluk.reader.domain.plans.effectiveReaderSettings
import com.pluk.reader.domain.plans.hasThemesAndFonts
import com.pluk.reader.domain.plans.isPaid
import com.pluk.reader.domain.profile.QuotaCache
import kotlinx.coroutines.flow.map

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
    private val readingSession: ReadingSession,
    quotaCache: QuotaCache,
    private val planPrompts: PlanPrompts,
    private val notes: NoteRepository,
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
    /** ANN-010: se abrió desde una nota; el libro abre en su lugar y no en la última posición. */
    private val noteId: String? = savedStateHandle[ARG_NOTE_ID]
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

    private val _noteEditor = MutableStateFlow<NoteEditor?>(null)

    /** ANN-008, ANN-009: la nota que se está escribiendo o mirando. Null si el editor está cerrado. */
    val noteEditor: StateFlow<NoteEditor?> = _noteEditor.asStateFlow()

    /** ANN-008: las notas del libro, que el lector muestra subrayadas. */
    val bookNotes: StateFlow<List<Note>> =
        notes.observe(bookId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    private data class Reading(val progress: Int?, val page: Int?, val chapter: String, val continueFrom: ContinueFrom?)

    private val position = combine(progress, pageNumber, chapterTitle, remoteAhead) { progress, page, chapter, remote ->
        Reading(
            progress, page, chapter,
            remote?.let { ContinueFrom(it.deviceName, progressPercent(it.position.progress)) },
        )
    }

    /** PLN-015: plan de la cuenta según la última cuota conocida; sin dato, Gratis. */
    private val plan = quotaCache.quota.map { PlanCatalog.planOf(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, PlanId.FREE)

    /** PLN-015: ajustes con los que se lee (en Gratis, Clásico y serifa); lo guardado no cambia. */
    private val effectiveSettings = combine(settingsRepository.settings, plan) { saved, plan ->
        effectiveReaderSettings(saved, plan)
    }

    val uiState: StateFlow<ReaderUiState> = combine(
        phase, combine(effectiveSettings, plan, ::Pair), position, controlsVisible, settingsOpen,
    ) { phase, (settings, plan), position, controls, settingsOpen ->
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
                readingOptionsLocked = !plan.hasThemesAndFonts,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), ReaderUiState.Loading)

    init {
        readingSession.opened(bookId)
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
            // ANN-010: abrir en una nota no pregunta por la posición de otro dispositivo: el usuario eligió el lugar.
            val note = noteId?.let { notes.get(it) }?.takeIf { it.bookId == bookId }
            if (note != null) {
                openBook(startAt = note.locatorJson)
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

    /** [startAt]: locator donde abrir en lugar de la última posición (ANN-010). */
    private suspend fun openBook(startAt: String? = null) {
        books.open(bookId).fold(
            onSuccess = { opened ->
                val book = if (startAt != null) opened.copy(resumeLocatorJson = startAt) else opened
                val settings: ReaderSettings = effectiveSettings.first()
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

    /**
     * ANN-008: el usuario eligió "Nota" sobre un texto seleccionado. [locatorJson] es el lugar de la selección y
     * [quote] el texto. Una selección vacía no abre nada.
     */
    fun onTextSelected(locatorJson: String, quote: String, totalProgression: Double?) {
        val text = quote.trim()
        if (text.isEmpty()) return
        _noteEditor.value = NoteEditor(null, text, "", locatorJson, totalProgression ?: 0.0)
    }

    /** ANN-008: guarda la nota y cierra el editor. Una nota sin texto no se guarda y el editor sigue abierto. */
    fun saveNote(text: String) {
        val editor = _noteEditor.value ?: return
        val body = text.trim()
        if (body.isEmpty()) return
        _noteEditor.value = null
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val existing = editor.noteId?.let { notes.get(it) }
            notes.save(
                existing?.copy(text = body, updatedAt = now)
                    ?: Note(UUID.randomUUID().toString(), bookId, editor.locatorJson, editor.quote, body, editor.progression, now, now),
            )
        }
    }

    /** ANN-009: el usuario tocó un subrayado; se muestra su nota para leerla, editarla o borrarla. */
    fun openNote(id: String) {
        viewModelScope.launch {
            val note = notes.get(id) ?: return@launch
            _noteEditor.value = NoteEditor(note.id, note.quote, note.text, note.locatorJson, note.progression)
        }
    }

    /** ANN-009: borra la nota abierta (la pantalla ya pidió confirmación); el subrayado se va con ella. */
    fun deleteNote() {
        val id = _noteEditor.value?.noteId ?: return
        _noteEditor.value = null
        viewModelScope.launch { notes.delete(id) }
    }

    /** ANN-008: cancelar no guarda nada. */
    fun closeNote() {
        _noteEditor.value = null
    }

    /** PLN-015: en Gratis, Sepia y Noche abren la hoja de función paga y no cambian la preferencia. */
    fun setTheme(theme: ReadingTheme) {
        if (planPrompts.allowReadingOption(plan.value, theme.isPaid)) updateSettings { it.withTheme(theme) }
    }

    /** PLN-015: en Gratis, sin serifa y monoespaciada abren la hoja de función paga. */
    fun setFont(font: ReaderFont) {
        if (planPrompts.allowReadingOption(plan.value, font.isPaid)) updateSettings { it.withFont(font) }
    }
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
        // LIB-020: si el libro se borró desde otro dispositivo mientras estaba abierto, se quita ahora.
        readingSession.closed(bookId)
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
        const val ARG_NOTE_ID = "noteId"
        private const val STOP_TIMEOUT_MS = 5_000L
        private const val SAVE_DEBOUNCE_MS = 250L
    }
}
