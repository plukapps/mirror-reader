package com.pluk.reader.library

import androidx.lifecycle.SavedStateHandle
import com.pluk.reader.domain.model.ImportOutcome
import com.pluk.reader.domain.model.LibraryBook
import com.pluk.reader.domain.model.LibraryFilter
import com.pluk.reader.domain.repository.LibraryRepository
import com.pluk.reader.domain.usecase.ImportBooksUseCase
import com.pluk.reader.domain.account.AccountRepository
import com.pluk.reader.domain.account.AccountUser
import com.pluk.reader.domain.account.QuotaSource
import com.pluk.reader.domain.account.StorageQuota
import com.pluk.reader.domain.remote.BookFileStore
import com.pluk.reader.domain.remote.RemoteBook
import com.pluk.reader.domain.remote.RemoteLibrary
import com.pluk.reader.domain.repository.BookUploadRepository
import com.pluk.reader.domain.repository.PendingUpload
import com.pluk.reader.domain.usecase.UploadBooksUseCase
import com.pluk.reader.domain.usecase.UploadReport
import com.pluk.reader.ui.library.toMessages
import java.io.File
import com.pluk.reader.ui.library.LibraryMessage
import com.pluk.reader.ui.library.LibraryUiState
import com.pluk.reader.ui.library.LibraryViewModel
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
class LibraryViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    private class FakeLibrary(initial: List<LibraryBook> = emptyList()) : LibraryRepository {
        val state = MutableStateFlow(initial)
        override val books = state
        val outcomes = mutableMapOf<String, ImportOutcome>()
        override suspend fun import(uri: String): ImportOutcome = outcomes.getValue(uri)
    }

    private fun book(id: String, progress: Int?) = LibraryBook(id, "T$id", null, null, progress)

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private class FakeAccount(signedIn: Boolean = false) : AccountRepository {
        val state = MutableStateFlow(if (signedIn) AccountUser("uid", "a@b.c") else null)
        override val user = state
        override suspend fun signIn(email: String, password: String) = Result.failure<AccountUser>(UnsupportedOperationException())
    }

    private class FakeUploads(var report: UploadReport = UploadReport(uploaded = 1)) : BookUploadRepository, BookFileStore, RemoteLibrary, QuotaSource {
        var runs = 0
        override suspend fun pending(): List<PendingUpload> {
            runs++
            return listOf(PendingUpload(RemoteBook("x", "T", emptyList(), 1), File("/x.epub")))
        }
        override suspend fun markUploaded(bookId: String, sizeBytes: Long) = Unit
        override suspend fun upload(bookId: String, file: File) = Result.success(Unit)
        override suspend fun download(bookId: String, destination: File) = Result.success(Unit)
        override suspend fun listBooks() = Result.success(emptyList<RemoteBook>())
        override suspend fun saveBook(book: RemoteBook) = Result.success(Unit)
        override suspend fun current() = Result.success(StorageQuota(0, 15L * 1024 * 1024))
    }

    private fun viewModel(
        library: FakeLibrary,
        filter: String? = null,
        account: FakeAccount = FakeAccount(),
        uploads: FakeUploads = FakeUploads(),
    ) = LibraryViewModel(
        library,
        ImportBooksUseCase(library),
        UploadBooksUseCase(uploads, uploads, uploads, uploads),
        account,
        SavedStateHandle(listOfNotNull(filter?.let { LibraryViewModel.ARG_FILTER to it }).toMap()),
    )

    private fun local(id: String, uploaded: Boolean = false, downloaded: Boolean = true) =
        LibraryBook(id, "T$id", null, null, null, isDownloaded = downloaded, isUploaded = uploaded)

    private suspend fun LibraryViewModel.ready(): LibraryUiState = uiState.first { !it.loading }

    // LIB-010, LIB-011
    @Test
    fun tabsShowCountsAndFilterTheGrid() = runTest(dispatcher) {
        val vm = viewModel(FakeLibrary(listOf(book("a", null), book("b", 42), book("c", 100))))
        val all = vm.ready()
        assertEquals(Triple(3, 1, 1), Triple(all.allCount, all.readingCount, all.finishedCount))
        assertEquals(3, all.books.size)

        vm.onFilterSelected(LibraryFilter.Reading)
        assertEquals(listOf("b"), vm.uiState.first { it.filter == LibraryFilter.Reading }.books.map { it.id })
        vm.onFilterSelected(LibraryFilter.Finished)
        assertEquals(listOf("c"), vm.uiState.first { it.filter == LibraryFilter.Finished }.books.map { it.id })
    }

    // LIB-011: el progreso se actualiza cuando cambia la posición guardada
    @Test
    fun gridFollowsProgressChanges() = runTest(dispatcher) {
        val library = FakeLibrary(listOf(book("a", 10)))
        val vm = viewModel(library)
        assertEquals(10, vm.ready().books.single().progressPercent)
        library.state.value = listOf(book("a", 55))
        assertEquals(55, vm.uiState.first { it.books.singleOrNull()?.progressPercent == 55 }.books.single().progressPercent)
    }

    @Test
    fun emptyLibraryIsReported() = runTest(dispatcher) {
        assertTrue(viewModel(FakeLibrary()).ready().isLibraryEmpty)
        assertFalse(viewModel(FakeLibrary(listOf(book("a", null)))).ready().isLibraryEmpty)
    }

    // LIB-001, LIB-002, LIB-003: avisos tras importar varios archivos
    @Test
    fun importReportsEachOutcome() = runTest(dispatcher) {
        val library = FakeLibrary()
        library.outcomes["a"] = ImportOutcome.Imported("1", "A")
        library.outcomes["b"] = ImportOutcome.Imported("2", "B")
        library.outcomes["c"] = ImportOutcome.AlreadyInLibrary("3", "C")
        library.outcomes["d"] = ImportOutcome.Rejected("dañado")
        val vm = viewModel(library)
        val received = mutableListOf<LibraryMessage>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.messages.collect { received += it } }
        advanceUntilIdle()

        vm.onImport(listOf("a", "b", "c", "d"))
        advanceUntilIdle()

        assertEquals(
            listOf(LibraryMessage.Imported(2), LibraryMessage.AlreadyInLibrary("C"), LibraryMessage.Rejected("dañado")),
            received,
        )
    }

    @Test
    fun importingFlagIsClearedAfterImport() = runTest(dispatcher) {
        val library = FakeLibrary()
        library.outcomes["a"] = ImportOutcome.Imported("1", "A")
        val vm = viewModel(library)
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()
        vm.onImport(listOf("a"))
        advanceUntilIdle()
        assertFalse(vm.uiState.value.importing)
    }

    @Test
    fun emptySelectionDoesNothing() = runTest(dispatcher) {
        val library = FakeLibrary()
        val vm = viewModel(library)
        val received = mutableListOf<LibraryMessage>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.messages.collect { received += it } }
        vm.onImport(emptyList())
        advanceUntilIdle()
        assertTrue(received.isEmpty())
    }

    // HOM-011: "Ver todo" de Inicio abre la biblioteca con el filtro de la sección
    @Test
    fun initialFilterComesFromTheRouteArgument() = runTest(dispatcher) {
        val library = FakeLibrary(listOf(book("a", null), book("b", 42), book("c", 100)))
        val finished = viewModel(library, filter = "Finished").ready()
        assertEquals(LibraryFilter.Finished, finished.filter)
        assertEquals(listOf("c"), finished.books.map { it.id })
        assertEquals(LibraryFilter.All, viewModel(library, filter = "basura").ready().filter)
        assertEquals(LibraryFilter.All, viewModel(library).ready().filter)
    }

    // LIB-007, SYN-001: el botón de subir aparece solo con sesión y libros pendientes (descargados y no subidos)
    @Test
    fun uploadIsOfferedOnlyWithASessionAndPendingBooks() = runTest(dispatcher) {
        val books = listOf(local("a"), local("b", uploaded = true), local("c", downloaded = false, uploaded = true))
        val withSession = viewModel(FakeLibrary(books), account = FakeAccount(signedIn = true)).ready()
        assertEquals(1, withSession.pendingUploadCount)
        assertTrue(withSession.canUpload)

        val noSession = viewModel(FakeLibrary(books), account = FakeAccount(signedIn = false)).ready()
        assertFalse(noSession.canUpload)

        val nothingPending = viewModel(FakeLibrary(listOf(local("b", uploaded = true))), account = FakeAccount(true)).ready()
        assertEquals(0, nothingPending.pendingUploadCount)
        assertFalse(nothingPending.canUpload)
    }

    @Test
    fun uploadReportsTheOutcomeAndClearsTheFlag() = runTest(dispatcher) {
        val vm = viewModel(FakeLibrary(listOf(local("a"))), account = FakeAccount(true))
        val received = mutableListOf<LibraryMessage>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.messages.collect { received += it } }
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()

        vm.onUpload()
        advanceUntilIdle()

        assertEquals(listOf<LibraryMessage>(LibraryMessage.Uploaded(1)), received)
        assertFalse(vm.uiState.value.uploading)
    }

    // Un segundo toque mientras sube no lanza otra tanda
    @Test
    fun aSecondUploadWhileRunningIsIgnored() = runTest(dispatcher) {
        val uploads = FakeUploads()
        val vm = viewModel(FakeLibrary(listOf(local("a"))), account = FakeAccount(true), uploads = uploads)
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()

        vm.onUpload()
        vm.onUpload()
        advanceUntilIdle()

        assertEquals(1, uploads.runs)
    }

    // LIB-009, SYN-008: avisos de cuota llena, falla y sin conexión
    @Test
    fun uploadReportsMapToMessages() {
        assertEquals(
            listOf<LibraryMessage>(LibraryMessage.Uploaded(2), LibraryMessage.NotEnoughSpace(1), LibraryMessage.UploadFailed(3)),
            UploadReport(uploaded = 2, notEnoughSpace = 1, failed = 3).toMessages(),
        )
        assertEquals(listOf<LibraryMessage>(LibraryMessage.CloudUnreachable), UploadReport(unreachable = true).toMessages())
        assertEquals(
            listOf<LibraryMessage>(LibraryMessage.Uploaded(1), LibraryMessage.CloudUnreachable),
            UploadReport(uploaded = 1, unreachable = true).toMessages(),
        )
        assertEquals(emptyList<LibraryMessage>(), UploadReport().toMessages())
    }
}
