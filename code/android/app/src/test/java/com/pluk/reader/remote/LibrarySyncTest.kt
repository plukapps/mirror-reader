package com.pluk.reader.remote

import com.pluk.reader.domain.account.AccountRepository
import com.pluk.reader.domain.account.AccountUser
import com.pluk.reader.domain.usecase.LibrarySync
import com.pluk.reader.domain.usecase.SyncIssue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** SYN-001, SYN-008: la sincronización arranca sola, nunca corre dos veces a la vez y no pierde pedidos. */
@OptIn(ExperimentalCoroutinesApi::class)
class LibrarySyncTest {
    private val dispatcher = StandardTestDispatcher()
    private val backend = FakeSyncBackend()

    private class FakeAccount(initial: AccountUser?) : AccountRepository {
        val state = MutableStateFlow(initial)
        override val user = state
        override suspend fun signIn(email: String, password: String) = Result.failure<AccountUser>(IllegalStateException())
    }

    private fun sync(account: FakeAccount) =
        LibrarySync(backend.useCase(), account, CoroutineScope(dispatcher)).also { it.start() }

    private val session = AccountUser("u1", null)

    @Test
    fun syncsOnStartWhenThereIsASession() = runTest(dispatcher) {
        sync(FakeAccount(session))
        advanceUntilIdle()
        assertEquals(1, backend.listings)
    }

    @Test
    fun doesNotSyncWithoutASession() = runTest(dispatcher) {
        val sync = sync(FakeAccount(null))
        sync.request()
        advanceUntilIdle()
        assertEquals(0, backend.listings)
    }

    // La sesión llega de forma asíncrona: sincroniza una vez, aunque cambie el correo del mismo usuario
    @Test
    fun syncsOnceWhenTheSessionArrivesLater() = runTest(dispatcher) {
        val account = FakeAccount(null)
        sync(account)
        advanceUntilIdle()
        account.state.value = session
        advanceUntilIdle()
        account.state.value = AccountUser("u1", "otro@mail")
        advanceUntilIdle()
        assertEquals(1, backend.listings)
    }

    // La actividad se recrea y vuelve a llamar start
    @Test
    fun startingTwiceSyncsOnce() = runTest(dispatcher) {
        val sync = sync(FakeAccount(session))
        sync.start()
        advanceUntilIdle()
        assertEquals(1, backend.listings)
    }

    // SYN-001: importar pide una pasada
    @Test
    fun aRequestRunsAnotherPass() = runTest(dispatcher) {
        val sync = sync(FakeAccount(session))
        advanceUntilIdle()
        sync.request()
        advanceUntilIdle()
        assertEquals(2, backend.listings)
    }

    // Un libro importado a mitad de una pasada no queda esperando, y varios pedidos juntos son uno solo
    @Test
    fun requestsDuringAPassCollapseIntoOneMorePass() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>()
        backend.listGate = gate
        val sync = sync(FakeAccount(session))
        advanceUntilIdle()
        assertTrue(sync.state.value.running)

        repeat(3) { sync.request() }
        backend.listGate = null
        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(2, backend.listings)
        assertFalse(sync.state.value.running)
    }

    // SYN-008: el estado muestra el problema de la última pasada y se limpia cuando sale bien
    @Test
    fun stateReportsTheLastIssueAndClearsItOnSuccess() = runTest(dispatcher) {
        backend.offline = true
        val sync = sync(FakeAccount(session))
        advanceUntilIdle()
        assertEquals(SyncIssue.Offline, sync.state.value.issue)
        assertFalse(sync.state.value.running)

        backend.offline = false
        sync.request()
        advanceUntilIdle()
        assertNull(sync.state.value.issue)
    }
}
