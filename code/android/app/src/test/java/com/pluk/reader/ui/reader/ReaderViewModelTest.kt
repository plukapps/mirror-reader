package com.pluk.reader.ui.reader

import androidx.lifecycle.SavedStateHandle
import com.pluk.reader.domain.model.BookOpenException
import com.pluk.reader.domain.model.LineSpacing
import com.pluk.reader.domain.model.OpenedBook
import com.pluk.reader.domain.model.ReaderFont
import com.pluk.reader.domain.model.ReaderSettings
import com.pluk.reader.domain.model.ReadingTheme
import com.pluk.reader.domain.remote.BookFileStore
import com.pluk.reader.domain.remote.RemoteBook
import com.pluk.reader.domain.remote.RemoteUnavailableException
import com.pluk.reader.domain.repository.BookRepository
import com.pluk.reader.domain.repository.CloudBooksRepository
import com.pluk.reader.domain.repository.CoverFile
import com.pluk.reader.domain.usecase.DownloadBookUseCase
import com.pluk.reader.domain.usecase.ResolveOpeningPositionUseCase
import java.io.File
import com.pluk.reader.domain.repository.PositionRepository
import com.pluk.reader.domain.repository.SettingsRepository
import com.pluk.reader.domain.account.AccountUser
import com.pluk.reader.remote.FakePositionBackend
import com.pluk.reader.remote.FakeSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.readium.r2.shared.publication.Publication

@OptIn(ExperimentalCoroutinesApi::class)
class ReaderViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    private class FakeBooks(var result: Result<OpenedBook>) : BookRepository {
        var opened: String? = null
        override suspend fun open(bookId: String): Result<OpenedBook> {
            opened = bookId
            return result
        }
    }

    private class FakeSettings(initial: ReaderSettings = ReaderSettings()) : SettingsRepository {
        val state = MutableStateFlow(initial)
        override val settings: StateFlow<ReaderSettings> = state
        override suspend fun update(transform: (ReaderSettings) -> ReaderSettings) {
            state.value = transform(state.value)
        }
    }

    private class FakePositions : PositionRepository {
        val saved = mutableMapOf<String, String>()
        val savedProgression = mutableMapOf<String, Double?>()

        /** Lo que hace la base real: la posición guardada queda pendiente de enviar. */
        var onSaved: (String) -> Unit = {}
        override suspend fun get(bookId: String): String? = saved[bookId]
        override suspend fun save(bookId: String, locatorJson: String, totalProgression: Double?) {
            saved[bookId] = locatorJson
            savedProgression[bookId] = totalProgression
            onSaved(bookId)
        }
    }

    private class FakeHost : NavigatorHost {
        var installedFor: OpenedBook? = null
        var installedSettings: ReaderSettings? = null
        var cleared = false
        var lastLocator: String? = null
        var externalLink: ((String) -> Unit)? = null
        override fun install(book: OpenedBook, settings: ReaderSettings, onExternalLink: (String) -> Unit) {
            installedFor = book
            installedSettings = settings
            externalLink = onExternalLink
        }

        override fun onLocatorChanged(locatorJson: String) {
            lastLocator = locatorJson
        }

        override fun clear() {
            cleared = true
        }
    }

    private val settings = FakeSettings()
    private val positions = FakePositions()
    private val host = FakeHost()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun book(id: String = "libro-1", resume: String? = null) = OpenedBook(
        publication = Publication(
            manifest = org.readium.r2.shared.publication.Manifest(
                metadata = org.readium.r2.shared.publication.Metadata(identifier = id),
            ),
        ),
        bookId = id,
        resumeLocatorJson = resume,
    )

    private class FakeCloud(var downloaded: Boolean = true, val installResult: Result<Unit> = Result.success(Unit)) :
        CloudBooksRepository {
        var installs = 0
        override suspend fun addCloudOnly(books: List<RemoteBook>) = Unit
        override suspend fun cloudOnlyBookIds(): List<String> = emptyList()
        override suspend fun coversToUpload(): List<CoverFile> = emptyList()
        override suspend fun markCoverUploaded(bookId: String) = Unit
        override suspend fun cloudBooksWithoutCover(): List<String> = emptyList()
        override suspend fun installCover(bookId: String, downloaded: File) = Unit
        override suspend fun isDownloaded(bookId: String) = downloaded
        override fun newTempFile(): File = File.createTempFile("test-", ".tmp")
        override suspend fun install(bookId: String, downloaded: File): Result<Unit> {
            installs++
            downloaded.delete()
            if (installResult.isSuccess) this.downloaded = true
            return installResult
        }
    }

    private class FakeFiles(val result: Result<Unit> = Result.success(Unit)) : BookFileStore {
        var downloads = 0
        override suspend fun upload(bookId: String, file: File) = Result.success(Unit)
        override suspend fun uploadCover(bookId: String, file: File) = Result.success(Unit)
        override suspend fun downloadCover(bookId: String, destination: File) = Result.success(false)
        override suspend fun download(bookId: String, destination: File): Result<Unit> {
            downloads++
            return result
        }
    }

    private val cloud = FakeCloud()
    private val files = FakeFiles()

    private val positionBackend = FakePositionBackend()
    private val positionSync = positionBackend.sync(FakeSession(AccountUser("u1", null)), CoroutineScope(dispatcher))
    private val resolveOpening by lazy {
        ResolveOpeningPositionUseCase(positionBackend, positionBackend, positions, FakeSession(AccountUser("u1", null)))
    }

    private fun viewModel(books: FakeBooks) = ReaderViewModel(
        SavedStateHandle(mapOf(ReaderViewModel.ARG_BOOK_ID to "hash-del-libro")), books, settings, positions, host,
        DownloadBookUseCase(cloud, files), positionSync, resolveOpening,
    )

    private suspend fun ReaderViewModel.awaitReady(predicate: (ReaderUiState.Ready) -> Boolean = { true }) =
        uiState.filterIsInstance<ReaderUiState.Ready>().first(predicate)

    // LIB-002: un libro que no abre muestra el mensaje y no instala el navegador
    @Test
    fun failedOpenShowsMessage() = runTest(dispatcher) {
        val vm = viewModel(FakeBooks(Result.failure(BookOpenException("No se pudo abrir el libro."))))
        val state = vm.uiState.first { it !is ReaderUiState.Loading }
        assertEquals(ReaderUiState.Failed("No se pudo abrir el libro."), state)
        assertNull(host.installedFor)
    }

    // LIB-007: abrir un libro solo en la nube lo descarga una vez y después lo abre
    @Test
    fun opensACloudOnlyBookAfterDownloadingIt() = runTest(dispatcher) {
        cloud.downloaded = false
        val books = FakeBooks(Result.success(book()))
        val vm = viewModel(books)
        vm.awaitReady()
        assertEquals(1, files.downloads)
        assertEquals(1, cloud.installs)
        assertEquals("hash-del-libro", books.opened)
    }

    // LIB-007: un libro que ya está en el dispositivo se abre sin tocar la red
    @Test
    fun doesNotDownloadABookThatIsAlreadyOnTheDevice() = runTest(dispatcher) {
        val vm = viewModel(FakeBooks(Result.success(book())))
        vm.awaitReady()
        assertEquals(0, files.downloads)
    }

    // SYN-001: sin conexión el libro de la nube no se abre y el mensaje lo dice; no se intenta abrir un archivo que no está
    @Test
    fun failedDownloadShowsMessageAndDoesNotOpen() = runTest(dispatcher) {
        cloud.downloaded = false
        val failing = FakeFiles(Result.failure(RemoteUnavailableException()))
        val books = FakeBooks(Result.success(book()))
        val vm = ReaderViewModel(
            SavedStateHandle(mapOf(ReaderViewModel.ARG_BOOK_ID to "hash-del-libro")), books, settings, positions, host,
            DownloadBookUseCase(cloud, failing), positionSync, resolveOpening,
        )
        val state = vm.uiState.first { it !is ReaderUiState.Loading }
        assertEquals(ReaderUiState.Failed("Sin conexión. No se pudo descargar el libro."), state)
        assertNull(books.opened)
        assertNull(host.installedFor)
    }

    // El libro que se abre sale del argumento de navegación
    @Test
    fun opensTheUriFromNavigationArgument() = runTest(dispatcher) {
        val books = FakeBooks(Result.failure(BookOpenException("x")))
        val vm = viewModel(books)
        vm.uiState.first { it !is ReaderUiState.Loading }
        assertEquals("hash-del-libro", books.opened)
    }

    // RDR-007: al abrir, el navegador se instala con los ajustes actuales
    @Test
    fun successfulOpenInstallsNavigatorWithCurrentSettings() = runTest(dispatcher) {
        settings.state.value = ReaderSettings(theme = ReadingTheme.DARK)
        val opened = book()
        val vm = viewModel(FakeBooks(Result.success(opened)))
        val ready = vm.awaitReady()
        assertEquals(opened, ready.book)
        assertEquals(opened, host.installedFor)
        assertEquals(ReadingTheme.DARK, host.installedSettings?.theme)
        assertTrue(ready.toc.isEmpty())
    }

    // RDR-002, RDR-003: los ajustes se persisten por el repositorio y llegan al estado
    @Test
    fun settingChangesArePersistedAndReflectedInState() = runTest(dispatcher) {
        val vm = viewModel(FakeBooks(Result.success(book())))
        vm.awaitReady()
        vm.setTheme(ReadingTheme.DARK)
        vm.biggerFont()
        val expected = ReaderSettings(theme = ReadingTheme.DARK, fontScale = 1.1)
        val ready = vm.awaitReady { it.settings == expected }
        assertEquals(expected, settings.state.value)
        assertEquals(expected, ready.settings)
    }

    // RDR-005
    @Test
    fun progressIsShownAsPercent() = runTest(dispatcher) {
        val vm = viewModel(FakeBooks(Result.success(book())))
        vm.awaitReady()
        vm.onLocatorChanged("""{"href":"ch1.xhtml"}""", 0.424)
        assertEquals(42, vm.awaitReady { it.progressPercent != null }.progressPercent)
    }

    // RDR-014: "Aa" abre y cierra el panel de ajustes, y cerrarlo no toca los controles
    @Test
    fun settingsPanelOpensAndCloses() = runTest(dispatcher) {
        val vm = viewModel(FakeBooks(Result.success(book())))
        assertEquals(false, vm.awaitReady().settingsOpen)
        vm.toggleSettings()
        assertEquals(true, vm.awaitReady { it.settingsOpen }.settingsOpen)
        vm.toggleSettings()
        assertEquals(false, vm.awaitReady { !it.settingsOpen }.settingsOpen)
        vm.toggleSettings()
        vm.awaitReady { it.settingsOpen }
        vm.closeSettings()
        val ready = vm.awaitReady { !it.settingsOpen }
        assertEquals(false, ready.settingsOpen)
        assertTrue(ready.controlsVisible)
    }

    // RDR-014: abrir el panel muestra los controles aunque estuvieran ocultos
    @Test
    fun openingSettingsShowsTheControls() = runTest(dispatcher) {
        val vm = viewModel(FakeBooks(Result.success(book())))
        vm.awaitReady()
        vm.hideControls()
        vm.awaitReady { !it.controlsVisible }
        vm.toggleSettings()
        val ready = vm.awaitReady { it.settingsOpen }
        assertTrue(ready.controlsVisible)
    }

    // RDR-003, RDR-014: el tema se elige directamente
    @Test
    fun themeIsPickedDirectlyAndPersisted() = runTest(dispatcher) {
        val vm = viewModel(FakeBooks(Result.success(book())))
        vm.awaitReady()
        vm.setTheme(ReadingTheme.SEPIA)
        assertEquals(ReadingTheme.SEPIA, vm.awaitReady { it.settings.theme == ReadingTheme.SEPIA }.settings.theme)
        assertEquals(ReadingTheme.SEPIA, settings.state.value.theme)
    }

    // RDR-002, RDR-014: tipo de letra e interlineado se eligen y se guardan sin tocar lo demás
    @Test
    fun fontAndLineSpacingArePickedAndPersisted() = runTest(dispatcher) {
        val vm = viewModel(FakeBooks(Result.success(book())))
        vm.awaitReady()
        vm.setFont(ReaderFont.MONO)
        vm.setLineSpacing(LineSpacing.WIDE)
        val expected = ReaderSettings(font = ReaderFont.MONO, lineSpacing = LineSpacing.WIDE)
        assertEquals(expected, vm.awaitReady { it.settings == expected }.settings)
        assertEquals(expected, settings.state.value)
    }

    // El título que sigue al capítulo se prueba en `ChapterTitleTest`: aquí no se pueden armar enlaces de Readium (usan android.net.Uri).
    // RDR-013: un libro sin tabla de contenidos no muestra título
    @Test
    fun chapterTitleIsEmptyWithoutTableOfContents() = runTest(dispatcher) {
        val vm = viewModel(FakeBooks(Result.success(book())))
        vm.awaitReady()
        vm.onLocatorChanged("""{"href":"c1.xhtml"}""", 0.1, href = "c1.xhtml")
        assertEquals("", vm.awaitReady { it.progressPercent != null }.chapterTitle)
    }

    // RDR-010
    // RDR-016: al girar se recrea el navegador; debe volver a la última posición, no a la del momento de abrir
    @Test
    fun latestLocatorIsHandedToTheNavigatorHost() = runTest(dispatcher) {
        val vm = viewModel(FakeBooks(Result.success(book())))
        vm.awaitReady()
        vm.onLocatorChanged("""{"href":"ch2.xhtml"}""", 0.5)
        assertEquals("""{"href":"ch2.xhtml"}""", host.lastLocator)
    }

    @Test
    fun pageNumberFollowsLocatorPosition() = runTest(dispatcher) {
        val vm = viewModel(FakeBooks(Result.success(book())))
        assertNull(vm.awaitReady().pageNumber)
        vm.onLocatorChanged("""{"href":"ch1.xhtml"}""", 0.1, position = 7)
        assertEquals(7, vm.awaitReady { it.pageNumber != null }.pageNumber)
        vm.onLocatorChanged("""{"href":"ch1.xhtml"}""", 0.2, position = 8)
        assertEquals(8, vm.awaitReady { it.pageNumber == 8 }.pageNumber)
    }

    // RDR-006: la posición se guarda por libro y sin escribir en cada evento
    @Test
    fun positionIsSavedDebouncedPerBook() = runTest(dispatcher) {
        val vm = viewModel(FakeBooks(Result.success(book("libro-7"))))
        vm.awaitReady()
        vm.onLocatorChanged("""{"p":1}""", 0.1)
        vm.onLocatorChanged("""{"p":2}""", 0.2)
        vm.onLocatorChanged("""{"p":3}""", 0.3)
        advanceTimeBy(100)
        assertTrue(positions.saved.isEmpty())
        advanceTimeBy(500)
        assertEquals(mapOf("libro-7" to """{"p":3}"""), positions.saved)
        // LIB-011: la progresión viaja con la posición para mostrarla en la biblioteca
        assertEquals(mapOf<String, Double?>("libro-7" to 0.3), positions.savedProgression)
    }

    @Test
    fun tappingCenterTogglesControls() = runTest(dispatcher) {
        val vm = viewModel(FakeBooks(Result.success(book())))
        assertTrue(vm.awaitReady().controlsVisible)
        vm.toggleControls()
        assertEquals(false, vm.awaitReady { !it.controlsVisible }.controlsVisible)
    }

    // El navegador se libera cuando la pantalla muere
    @Test
    fun clearingTheViewModelReleasesTheNavigator() = runTest(dispatcher) {
        val vm = viewModel(FakeBooks(Result.success(book())))
        vm.awaitReady()
        vm.release()
        assertTrue(host.cleared)
    }

    // RDR-009: el ajuste de animación se persiste por el repositorio
    @Test
    fun pageAnimationToggleIsPersisted() = runTest(dispatcher) {
        val vm = viewModel(FakeBooks(Result.success(book())))
        vm.awaitReady()
        vm.togglePageAnimation()
        assertEquals(false, vm.awaitReady { !it.settings.pageAnimation }.settings.pageAnimation)
        assertEquals(false, settings.state.value.pageAnimation)
    }

    // RDR-009: al pasar de página se ocultan los controles (para que no salgan en la captura)
    @Test
    fun hidingControlsIsIdempotent() = runTest(dispatcher) {
        val vm = viewModel(FakeBooks(Result.success(book())))
        assertTrue(vm.awaitReady().controlsVisible)
        vm.hideControls()
        vm.hideControls()
        assertEquals(false, vm.awaitReady { !it.controlsVisible }.controlsVisible)
    }

    // SYN-011: al cerrar el libro se guarda la última posición y se envía a la nube
    @Test
    fun closingTheBookSavesTheLastPositionAndSendsIt() = runTest(dispatcher) {
        val vm = viewModel(FakeBooks(Result.success(book())))
        vm.awaitReady()
        positions.onSaved = { id ->
            positionBackend.calls += "guardar:$id"
            positionBackend.savedLocally(id, readAt = 5)
        }
        vm.onLocatorChanged("""{"p":9}""", 0.9)

        // runCurrent, no advanceUntilIdle: al cerrar, Android cancela viewModelScope y el guardado con espera de
        // 250 ms no llega a correr. Aquí solo corre lo que el cierre dejó listo en el scope de la aplicación.
        vm.close()
        testScheduler.runCurrent()

        assertEquals(mapOf("libro-1" to """{"p":9}"""), positions.saved)
        // Primero se guarda, después se envía
        assertEquals(listOf("guardar:libro-1", "enviar:libro-1"), positionBackend.calls)
    }

    // Si no se leyó nada, cerrar no guarda ni envía
    @Test
    fun closingWithoutReadingSendsNothing() = runTest(dispatcher) {
        val vm = viewModel(FakeBooks(Result.success(book())))
        vm.awaitReady()

        vm.close()
        testScheduler.advanceUntilIdle()

        assertTrue(positions.saved.isEmpty())
        assertTrue(positionBackend.calls.isEmpty())
    }

    // El id del libro sale del argumento de navegación, no del libro abierto.
    private val OPENING_ID = "hash-del-libro"

    private fun localAt(progress: Double, readAt: Long = 10) {
        positionBackend.local[OPENING_ID] = com.pluk.reader.domain.model.LocalPosition(
            com.pluk.reader.domain.model.ReadingPosition(OPENING_ID, "local", progress, readAt),
            isSynced = true,
        )
    }

    private fun remoteAt(progress: Double, readAt: Long = 50) {
        positionBackend.fetchResult = Result.success(
            com.pluk.reader.domain.model.RemotePosition(
                com.pluk.reader.domain.model.ReadingPosition(OPENING_ID, "remota", progress, readAt),
                "otro-dispositivo",
                "SM-X510",
            ),
        )
    }

    // SYN-003: una diferencia grande con otro dispositivo se pregunta antes de abrir el libro
    @Test
    fun aBigDifferenceAsksBeforeOpeningTheBook() = runTest(dispatcher) {
        localAt(0.10)
        remoteAt(0.60)
        val books = FakeBooks(Result.success(book()))
        val vm = viewModel(books)

        val state = vm.uiState.first { it !is ReaderUiState.Loading }

        assertEquals(ReaderUiState.ResumePrompt("SM-X510", 60), state)
        assertNull(books.opened)
        assertNull(host.installedFor)
    }

    @Test
    fun continuingFromTheOtherDeviceAppliesItAndOpensTheBook() = runTest(dispatcher) {
        localAt(0.10)
        remoteAt(0.60)
        val books = FakeBooks(Result.success(book()))
        val vm = viewModel(books)
        vm.uiState.first { it is ReaderUiState.ResumePrompt }

        vm.onResumeChoice(useRemote = true)
        vm.awaitReady()

        assertEquals("remota", positionBackend.local.getValue(OPENING_ID).position.locatorJson)
        assertTrue(books.opened != null)
    }

    // El usuario se queda: su posición se guarda de nuevo para ser la más reciente
    @Test
    fun stayingKeepsTheLocalPositionAndOpensTheBook() = runTest(dispatcher) {
        localAt(0.10)
        remoteAt(0.60)
        val books = FakeBooks(Result.success(book()))
        val vm = viewModel(books)
        vm.uiState.first { it is ReaderUiState.ResumePrompt }

        vm.onResumeChoice(useRemote = false)
        vm.awaitReady()

        assertEquals("local", positionBackend.local.getValue(OPENING_ID).position.locatorJson)
        assertEquals(mapOf(OPENING_ID to "local"), positions.saved)
        assertTrue(books.opened != null)
    }

    // SYN-003: una diferencia chica salta sola, sin preguntar
    @Test
    fun aSmallDifferenceOpensTheBookWithoutAsking() = runTest(dispatcher) {
        localAt(0.50)
        remoteAt(0.51)
        val vm = viewModel(FakeBooks(Result.success(book())))
        vm.awaitReady()
        assertEquals("remota", positionBackend.local.getValue(OPENING_ID).position.locatorJson)
    }

    @Test
    fun choosingWhenNothingWasAskedDoesNothing() = runTest(dispatcher) {
        val vm = viewModel(FakeBooks(Result.success(book())))
        vm.awaitReady()
        vm.onResumeChoice(useRemote = true)
        assertTrue(positions.saved.isEmpty())
    }

    // --- SYN-013: aviso mientras se lee ---

    private suspend fun kotlinx.coroutines.test.TestScope.otherDeviceReads(progress: Double, bookId: String = OPENING_ID) {
        positionSync.startListening()
        testScheduler.advanceUntilIdle()
        positionBackend.changes.emit(
            Result.success(
                com.pluk.reader.domain.remote.RemoteChanges(
                    listOf(
                        com.pluk.reader.domain.model.RemotePosition(
                            com.pluk.reader.domain.model.ReadingPosition(bookId, "otro-lugar", progress, 99),
                            "otro-dispositivo",
                            "SM-X510",
                        ),
                    ),
                    1_000L,
                ),
            ),
        )
        testScheduler.advanceUntilIdle()
    }

    /** Estado actual del lector, con la suscripción activa para que se recalcule (el cache de `stateIn` puede estar viejo). */
    private fun kotlinx.coroutines.test.TestScope.hotState(vm: ReaderViewModel): () -> ReaderUiState {
        backgroundScope.launch { vm.uiState.collect {} }
        return { vm.uiState.value }
    }

    private suspend fun ReaderViewModel.readyAt(progress: Double): ReaderUiState.Ready {
        awaitReady()
        onLocatorChanged("""{"p":1}""", progress)
        return awaitReady()
    }

    @Test
    fun anotherDeviceAheadOffersToContinueFromThere() = runTest(dispatcher) {
        localAt(0.10)
        val vm = viewModel(FakeBooks(Result.success(book())))
        vm.readyAt(0.10)

        otherDeviceReads(0.60)

        assertEquals(ContinueFrom("SM-X510", 60), vm.awaitReady { it.continueFrom != null }.continueFrom)
    }

    // El otro dispositivo quedó atrás o casi igual: no hay nada que ofrecer
    @Test
    fun noOfferWhenTheOtherDeviceIsBehindOrAlmostTheSame() = runTest(dispatcher) {
        localAt(0.50)
        val vm = viewModel(FakeBooks(Result.success(book())))
        vm.readyAt(0.50)
        val state = hotState(vm)

        otherDeviceReads(0.30)
        otherDeviceReads(0.51)

        assertNull((state() as ReaderUiState.Ready).continueFrom)
    }

    // Una posición de otro libro no interrumpe la lectura de este
    @Test
    fun anotherBooksPositionIsIgnored() = runTest(dispatcher) {
        localAt(0.10)
        val vm = viewModel(FakeBooks(Result.success(book())))
        vm.readyAt(0.10)
        val state = hotState(vm)

        otherDeviceReads(0.90, bookId = "otro-libro")

        assertNull((state() as ReaderUiState.Ready).continueFrom)
    }

    // SYN-013: la página nunca se mueve sola; solo cuando el usuario acepta
    @Test
    fun acceptingNavigatesToTheOtherDevicesPositionAndClearsTheNotice() = runTest(dispatcher) {
        localAt(0.10)
        val vm = viewModel(FakeBooks(Result.success(book())))
        vm.readyAt(0.10)
        val jumps = mutableListOf<String>()
        backgroundScope.launch(kotlinx.coroutines.test.UnconfinedTestDispatcher(testScheduler)) { vm.jumps.collect { jumps += it } }
        otherDeviceReads(0.60)
        vm.awaitReady { it.continueFrom != null }
        assertTrue(jumps.isEmpty())

        vm.continueFromOtherDevice()

        assertEquals(listOf("otro-lugar"), jumps)
        assertNull(vm.awaitReady { it.continueFrom == null }.continueFrom)
    }

    @Test
    fun dismissingClearsTheNoticeWithoutNavigating() = runTest(dispatcher) {
        localAt(0.10)
        val vm = viewModel(FakeBooks(Result.success(book())))
        vm.readyAt(0.10)
        val jumps = mutableListOf<String>()
        backgroundScope.launch(kotlinx.coroutines.test.UnconfinedTestDispatcher(testScheduler)) { vm.jumps.collect { jumps += it } }
        otherDeviceReads(0.60)
        vm.awaitReady { it.continueFrom != null }

        vm.dismissContinueFrom()

        assertNull(vm.awaitReady { it.continueFrom == null }.continueFrom)
        assertTrue(jumps.isEmpty())
    }

    // El aviso se retira solo cuando la lectura de aquí alcanza a la del otro dispositivo
    @Test
    fun theNoticeDisappearsWhenReadingCatchesUp() = runTest(dispatcher) {
        localAt(0.10)
        val vm = viewModel(FakeBooks(Result.success(book())))
        vm.readyAt(0.10)
        otherDeviceReads(0.60)
        vm.awaitReady { it.continueFrom != null }

        vm.onLocatorChanged("""{"p":2}""", 0.30)
        assertEquals(60, vm.awaitReady { it.progressPercent == 30 }.continueFrom?.percent)

        vm.onLocatorChanged("""{"p":3}""", 0.59)
        assertNull(vm.awaitReady { it.progressPercent == 59 }.continueFrom)
    }

    @Test
    fun continuingWithNothingOfferedDoesNothing() = runTest(dispatcher) {
        val vm = viewModel(FakeBooks(Result.success(book())))
        vm.awaitReady()
        val jumps = mutableListOf<String>()
        backgroundScope.launch(kotlinx.coroutines.test.UnconfinedTestDispatcher(testScheduler)) { vm.jumps.collect { jumps += it } }
        vm.continueFromOtherDevice()
        assertTrue(jumps.isEmpty())
    }
}
