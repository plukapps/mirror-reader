package com.pluk.reader.library

import com.pluk.reader.remote.NoRemovals
import com.pluk.reader.domain.account.AccountRepository
import com.pluk.reader.domain.account.AccountUser
import com.pluk.reader.domain.model.ImportOutcome
import com.pluk.reader.domain.model.LibraryBook
import com.pluk.reader.domain.repository.LibraryRepository
import com.pluk.reader.domain.usecase.LibrarySync
import com.pluk.reader.remote.FakePositionBackend
import com.pluk.reader.remote.FakeSession
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

    private val positionBackend = FakePositionBackend()

    private val prompts = com.pluk.reader.domain.plans.PlanPrompts()
    private val quotaCache = com.pluk.reader.plans.FakeQuotaCache()
    private val pendingUploads = com.pluk.reader.plans.FakePendingUploads()

    private fun viewModel(library: FakeLibrary = FakeLibrary()): MainViewModel {
        val account = FakeAccount(AccountUser("u1", null))
        return MainViewModel(
            library,
            LibrarySync(backend.useCase(), account, CoroutineScope(dispatcher)),
            positionBackend.sync(FakeSession(AccountUser("u1", null)), CoroutineScope(dispatcher)),
            NoRemovals,
            account,
            com.pluk.reader.onboarding.FakeAuth(),
            com.pluk.reader.domain.plans.ImportQuotaCheck(quotaCache, pendingUploads, prompts),
            com.pluk.reader.onboarding.FakeWelcomePreference(),
        )
    }

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

    // PLN-007: "Abrir con" con el espacio lleno abre el libro igual y muestra la hoja
    @Test
    fun anIncomingBookThatDoesNotFitStillOpensAndShowsTheSheet() = runTest(dispatcher) {
        val mib = 1024L * 1024
        quotaCache.quota.value = com.pluk.reader.domain.account.StorageQuota(15 * mib, 15 * mib)
        pendingUploads.items = com.pluk.reader.plans.FakePendingUploads("1" to mib).items
        val vm = viewModel(FakeLibrary(ImportOutcome.Imported("1", "A")))

        val incoming = vm.importIncoming("content://libro.epub")

        assertEquals(MainViewModel.Incoming.Open("1"), incoming)
        assertEquals(
            com.pluk.reader.domain.plans.PlanPrompt.Quota("A", com.pluk.reader.domain.plans.QuotaBlock.Full, 15 * mib),
            prompts.current.value,
        )
    }

    // SYN-011, SYN-012: al volver a la app se reenvía lo pendiente y empieza a llegar lo de otros dispositivos
    @Test
    fun comingToTheForegroundSendsPendingPositionsAndStartsListening() = runTest(dispatcher) {
        positionBackend.savedLocally("a", readAt = 10)
        val vm = viewModel()

        vm.onAppStarted()
        advanceUntilIdle()

        assertEquals(listOf("enviar:a"), positionBackend.calls)
        assertEquals(1, positionBackend.observedSince.size)
    }

    // Sin trabajo en segundo plano: al dejar de verse se envía y se deja de escuchar
    @Test
    fun leavingTheForegroundSendsAndStopsListening() = runTest(dispatcher) {
        val vm = viewModel()
        vm.onAppStarted()
        advanceUntilIdle()
        positionBackend.savedLocally("a", readAt = 10, synced = true)

        vm.onAppStopped()
        positionBackend.savedLocally("b", readAt = 20)
        advanceUntilIdle()
        positionBackend.changes.emit(
            Result.success(com.pluk.reader.domain.remote.RemoteChanges(listOf(positionBackend.remotePosition("a", readAt = 50)), 1L)),
        )
        advanceUntilIdle()

        assertEquals(10L, positionBackend.local.getValue("a").position.readAt)
        assertEquals(listOf("enviar:b"), positionBackend.calls)
    }
}
