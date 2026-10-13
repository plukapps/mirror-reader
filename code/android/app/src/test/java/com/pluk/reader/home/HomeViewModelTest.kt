package com.pluk.reader.home

import com.pluk.reader.domain.model.ImportOutcome
import com.pluk.reader.domain.model.LibraryBook
import com.pluk.reader.domain.repository.LibraryRepository
import com.pluk.reader.domain.usecase.ImportBooksUseCase
import com.pluk.reader.domain.account.StorageQuota
import com.pluk.reader.domain.plans.ImportQuotaCheck
import com.pluk.reader.domain.plans.ImportSource
import com.pluk.reader.domain.plans.PaidFeature
import com.pluk.reader.domain.plans.PlanPrompt
import com.pluk.reader.domain.plans.PlanPrompts
import com.pluk.reader.domain.plans.QuotaBlock
import com.pluk.reader.plans.FakePendingUploads
import com.pluk.reader.plans.FakeQuotaCache
import com.pluk.reader.ui.home.HomeViewModel
import com.pluk.reader.ui.library.LibraryMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    private class FakeLibrary(initial: List<LibraryBook> = emptyList()) : LibraryRepository {
        val state = MutableStateFlow(initial)
        override val books = state
        override suspend fun import(uri: String): ImportOutcome = ImportOutcome.Imported("nuevo", "Nuevo")
    }

    private fun book(id: String, progress: Int?, readAt: Long? = null) =
        LibraryBook(id, "T$id", null, null, progress, readAt)

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private var syncRequests = 0
    private val prompts = PlanPrompts()
    private val quotaCache = FakeQuotaCache()
    private val pendingUploads = FakePendingUploads()

    private fun viewModel(library: FakeLibrary) = HomeViewModel(
        library,
        ImportBooksUseCase(library),
        { syncRequests++ },
        ImportQuotaCheck(quotaCache, pendingUploads, prompts),
        prompts,
    )

    // HOM-002, HOM-004
    @Test
    fun exposesContinueReadingAndForYou() = runTest(dispatcher) {
        val vm = viewModel(FakeLibrary(listOf(book("n", null), book("l", 42, 5))))
        val state = vm.uiState.first { !it.loading }
        assertEquals("l", state.content.continueReading?.id)
        assertEquals(listOf("n", "l"), state.content.recentlyAdded.map { it.id })
    }

    // HOM-002: al volver del lector el progreso cambia y Inicio lo refleja
    @Test
    fun followsLibraryChanges() = runTest(dispatcher) {
        val library = FakeLibrary(listOf(book("l", 42, 5)))
        val vm = viewModel(library)
        assertEquals("l", vm.uiState.first { !it.loading }.content.continueReading?.id)
        library.state.value = listOf(book("l", 100, 6))
        assertEquals(null, vm.uiState.first { it.content.continueReading == null }.content.continueReading)
    }

    // HOM-003: biblioteca vacía
    @Test
    fun emptyLibraryIsReported() = runTest(dispatcher) {
        val state = viewModel(FakeLibrary()).uiState.first { !it.loading }
        assertTrue(state.content.libraryEmpty)
        assertFalse(state.content.recentlyAdded.isNotEmpty())
    }

    // HOM-003 con LIB-001: importar desde Inicio avisa el resultado
    @Test
    fun importFromHomeReportsMessage() = runTest(dispatcher) {
        val vm = viewModel(FakeLibrary())
        val received = mutableListOf<LibraryMessage>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.messages.collect { m -> received += m } }
        advanceUntilIdle()
        vm.onImport(listOf("content://x"))
        advanceUntilIdle()
        assertEquals(listOf<LibraryMessage>(LibraryMessage.Imported(1)), received)
    }

    // SYN-001: importar desde Inicio pide una pasada, como desde la Biblioteca
    @Test
    fun importFromHomeRequestsASync() = runTest(dispatcher) {
        val vm = viewModel(FakeLibrary())
        vm.onImport(listOf("content://x"))
        advanceUntilIdle()
        assertEquals(1, syncRequests)
    }

    // PLN-008: desde Inicio también se avisa si el libro no entra en la nube
    @Test
    fun importFromHomeChecksTheQuota() = runTest(dispatcher) {
        val mib = 1024L * 1024
        quotaCache.quota.value = StorageQuota(12 * mib, 15 * mib)
        pendingUploads.items = FakePendingUploads("nuevo" to 8 * mib).items
        val vm = viewModel(FakeLibrary())
        vm.onImport(listOf("content://x"))
        advanceUntilIdle()
        assertEquals(PlanPrompt.Quota("Nuevo", QuotaBlock.DoesNotFit(8 * mib, 3 * mib), 15 * mib), prompts.current.value)
    }

    // PLN-010: "Desde este dispositivo" abre el selector; Drive y Calibre, la hoja de función paga
    @Test
    fun importSourcesOpenThePickerOrThePaidSheet() = runTest(dispatcher) {
        val vm = viewModel(FakeLibrary())
        assertTrue(vm.onImportSource(ImportSource.DEVICE))
        assertEquals(null, prompts.current.value)
        assertFalse(vm.onImportSource(ImportSource.DRIVE))
        assertEquals(PlanPrompt.Paid(PaidFeature.DRIVE), prompts.current.value)
        assertFalse(vm.onImportSource(ImportSource.CALIBRE))
        assertEquals(PlanPrompt.Paid(PaidFeature.CALIBRE), prompts.current.value)
    }
}
