package com.pluk.reader.library

import com.pluk.reader.domain.account.AccountRepository
import com.pluk.reader.domain.account.AccountUser
import com.pluk.reader.domain.model.ImportOutcome
import com.pluk.reader.domain.model.LibraryBook
import com.pluk.reader.domain.remote.BookFileStore
import com.pluk.reader.domain.remote.RemoteBook
import com.pluk.reader.domain.remote.RemoteLibrary
import com.pluk.reader.domain.repository.CloudBooksRepository
import com.pluk.reader.domain.repository.CoverFile
import com.pluk.reader.domain.repository.LibraryRepository
import com.pluk.reader.domain.usecase.SyncCoversUseCase
import com.pluk.reader.domain.usecase.SyncRemoteBooksUseCase
import com.pluk.reader.ui.MainViewModel
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/** LIB-007: al abrir la app con sesión, los libros de la nube aparecen en la biblioteca. */
@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    private class FakeLibrary : LibraryRepository {
        override val books: Flow<List<LibraryBook>> = flowOf(emptyList())
        override suspend fun import(uri: String): ImportOutcome = ImportOutcome.Rejected("x")
    }

    private class FakeAccount(initial: AccountUser?) : AccountRepository {
        val state = MutableStateFlow(initial)
        override val user = state
        override suspend fun signIn(email: String, password: String) = Result.failure<AccountUser>(IllegalStateException())
    }

    private class FakeRemote : RemoteLibrary {
        var listings = 0
        override suspend fun listBooks(): Result<List<RemoteBook>> {
            listings++
            return Result.success(listOf(RemoteBook("a", "A", emptyList(), 1)))
        }
        override suspend fun saveBook(book: RemoteBook) = Result.success(Unit)
    }

    private class FakeCloud : CloudBooksRepository {
        val added = mutableListOf<String>()
        override suspend fun addCloudOnly(books: List<RemoteBook>) {
            added += books.map { it.id }
        }
        override suspend fun uploadedBooksWithCover(): List<CoverFile> = listOf(CoverFile("a", File("/portadas/a.jpg")))
        override suspend fun cloudBooksWithoutCover(): List<String> = emptyList()
        override suspend fun installCover(bookId: String, downloaded: File) = Unit
        override suspend fun isDownloaded(bookId: String) = true
        override fun newTempFile(): File = File("x")
        override suspend fun install(bookId: String, downloaded: File) = Result.success(Unit)
    }

    private class FakeFiles : BookFileStore {
        var coverUploads = 0
        override suspend fun upload(bookId: String, file: File) = Result.success(Unit)
        override suspend fun download(bookId: String, destination: File) = Result.success(Unit)
        override suspend fun uploadCover(bookId: String, file: File): Result<Unit> {
            coverUploads++
            return Result.success(Unit)
        }
        override suspend fun downloadCover(bookId: String, destination: File) = Result.success(false)
    }

    private val files = FakeFiles()
    private val remote = FakeRemote()
    private val cloud = FakeCloud()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(account: FakeAccount) =
        MainViewModel(FakeLibrary(), account, SyncRemoteBooksUseCase(remote, cloud), SyncCoversUseCase(cloud, files)).also { it.syncCloudBooks() }

    @Test
    fun syncsTheCloudBooksWhenThereIsASession() = runTest(dispatcher) {
        viewModel(FakeAccount(AccountUser("u1", null)))
        advanceUntilIdle()
        assertEquals(listOf("a"), cloud.added)
    }

    // LIB-012: después de los libros se sincronizan las portadas
    @Test
    fun syncsTheCoversAfterTheBooks() = runTest(dispatcher) {
        viewModel(FakeAccount(AccountUser("u1", null)))
        advanceUntilIdle()
        assertEquals(1, files.coverUploads)
    }

    // Llamarlo dos veces (la actividad se recrea) no sincroniza dos veces
    @Test
    fun callingItTwiceSyncsOnce() = runTest(dispatcher) {
        val vm = viewModel(FakeAccount(AccountUser("u1", null)))
        vm.syncCloudBooks()
        advanceUntilIdle()
        assertEquals(1, remote.listings)
    }

    @Test
    fun doesNotSyncWithoutASession() = runTest(dispatcher) {
        viewModel(FakeAccount(null))
        advanceUntilIdle()
        assertEquals(0, remote.listings)
    }

    // La sesión que llega después de arrancar (el inicio de sesión es asíncrono) también sincroniza, una vez
    @Test
    fun syncsOnceWhenTheSessionArrivesLater() = runTest(dispatcher) {
        val account = FakeAccount(null)
        viewModel(account)
        advanceUntilIdle()
        account.state.value = AccountUser("u1", null)
        advanceUntilIdle()
        account.state.value = AccountUser("u1", "otro@mail")
        advanceUntilIdle()
        assertEquals(1, remote.listings)
    }
}
