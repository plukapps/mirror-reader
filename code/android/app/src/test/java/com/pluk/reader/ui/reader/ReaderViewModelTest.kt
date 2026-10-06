package com.pluk.reader.ui.reader

import androidx.lifecycle.SavedStateHandle
import com.pluk.reader.domain.model.BookOpenException
import com.pluk.reader.domain.model.OpenedBook
import com.pluk.reader.domain.model.ReaderSettings
import com.pluk.reader.domain.model.ReadingTheme
import com.pluk.reader.domain.repository.BookRepository
import com.pluk.reader.domain.repository.PositionRepository
import com.pluk.reader.domain.repository.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
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
        override suspend fun open(uri: String): Result<OpenedBook> {
            opened = uri
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
        override suspend fun get(bookId: String): String? = saved[bookId]
        override suspend fun save(bookId: String, locatorJson: String) {
            saved[bookId] = locatorJson
        }
    }

    private class FakeHost : NavigatorHost {
        var installedFor: OpenedBook? = null
        var installedSettings: ReaderSettings? = null
        var cleared = false
        var externalLink: ((String) -> Unit)? = null
        override fun install(book: OpenedBook, settings: ReaderSettings, onExternalLink: (String) -> Unit) {
            installedFor = book
            installedSettings = settings
            externalLink = onExternalLink
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

    private fun viewModel(books: FakeBooks) = ReaderViewModel(
        SavedStateHandle(mapOf("uri" to "file:///libro.epub")), books, settings, positions, host,
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

    // El libro que se abre sale del argumento de navegación
    @Test
    fun opensTheUriFromNavigationArgument() = runTest(dispatcher) {
        val books = FakeBooks(Result.failure(BookOpenException("x")))
        val vm = viewModel(books)
        vm.uiState.first { it !is ReaderUiState.Loading }
        assertEquals("file:///libro.epub", books.opened)
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

    // RDR-001, RDR-002, RDR-003: los ajustes se persisten por el repositorio y llegan al estado
    @Test
    fun settingChangesArePersistedAndReflectedInState() = runTest(dispatcher) {
        val vm = viewModel(FakeBooks(Result.success(book())))
        vm.awaitReady()
        vm.toggleScroll()
        vm.nextTheme()
        vm.biggerFont()
        val expected = ReaderSettings(scroll = true, theme = ReadingTheme.DARK, fontScale = 1.1)
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
}
