package com.pluk.reader.library

import com.pluk.reader.domain.account.AccountRepository
import com.pluk.reader.domain.account.AccountUser
import com.pluk.reader.domain.model.ImportOutcome
import com.pluk.reader.domain.model.LibraryBook
import com.pluk.reader.domain.repository.LibraryRepository
import com.pluk.reader.domain.usecase.LibrarySync
import com.pluk.reader.remote.FakeSyncBackend
import com.pluk.reader.ui.MainViewModel
import kotlinx.coroutines.CoroutineScope
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** SYN-001: al abrir la app con sesión y al recibir un libro con "Abrir con", la biblioteca se sincroniza sola. */
@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val backend = FakeSyncBackend()

    private class FakeLibrary(private val outcome: ImportOutcome = ImportOutcome.Rejected("x")) : LibraryRepository {
        override val books: Flow<List<LibraryBook>> = flowOf(emptyList())
        override suspend fun import(uri: String): ImportOutcome = outcome
    }

    private class FakeAccount(initial: AccountUser?) : AccountRepository {
        override val user = MutableStateFlow(initial)
        override suspend fun signIn(email: String, password: String) = Result.failure<AccountUser>(IllegalStateException())
    }

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(library: FakeLibrary = FakeLibrary()) =
        MainViewModel(library, LibrarySync(backend.useCase(), FakeAccount(AccountUser("u1", null)), CoroutineScope(dispatcher)))

    @Test
    fun startingTheSyncSyncsTheLibrary() = runTest(dispatcher) {
        viewModel().startLibrarySync()
        advanceUntilIdle()
        assertEquals(1, backend.listings)
    }

    // Llamarlo dos veces (la actividad se recrea) no sincroniza dos veces
    @Test
    fun callingItTwiceSyncsOnce() = runTest(dispatcher) {
        val vm = viewModel()
        vm.startLibrarySync()
        vm.startLibrarySync()
        advanceUntilIdle()
        assertEquals(1, backend.listings)
    }

    // SYN-001: volver a la app justo después de arrancar no repite la pasada (TTL)
    @Test
    fun comingBackRightAfterStartingDoesNotSyncAgain() = runTest(dispatcher) {
        val vm = viewModel()
        vm.startLibrarySync()
        advanceUntilIdle()

        vm.onAppStarted()
        advanceUntilIdle()

        assertEquals(1, backend.listings)
    }

    // SYN-001: un libro recibido con "Abrir con" se sube solo
    @Test
    fun anIncomingBookRequestsASync() = runTest(dispatcher) {
        val vm = viewModel(FakeLibrary(ImportOutcome.Imported("1", "A")))
        vm.startLibrarySync()
        advanceUntilIdle()
        backend.pending += backend.pendingBook("1")

        val incoming = vm.importIncoming("content://libro.epub")
        advanceUntilIdle()

        assertTrue(incoming is MainViewModel.Incoming.Open)
        assertEquals(2, backend.listings)
        assertEquals(listOf("1"), backend.uploaded)
    }
}
