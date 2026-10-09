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

    private var clock = 0L
    private val ttl = 5 * 60 * 1000L

    /** Con reloj controlado por el test, para probar el TTL. */
    private fun syncWithClock(account: FakeAccount) =
        LibrarySync(backend.useCase(), account, CoroutineScope(dispatcher), { clock }, ttl).also { it.start() }

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

    // SYN-001: al volver a la app dentro del TTL no se repite la pasada
    @Test
    fun comingBackWithinTheTtlDoesNotSyncAgain() = runTest(dispatcher) {
        val sync = syncWithClock(FakeAccount(session))
        advanceUntilIdle()
        clock += ttl - 1

        sync.requestIfStale()
        advanceUntilIdle()

        assertEquals(1, backend.listings)
    }

    // SYN-001: pasado el TTL, volver a la app sincroniza (sin esperar a que el proceso muera)
    @Test
    fun comingBackAfterTheTtlSyncsAgain() = runTest(dispatcher) {
        val sync = syncWithClock(FakeAccount(session))
        advanceUntilIdle()
        clock += ttl

        sync.requestIfStale()
        advanceUntilIdle()

        assertEquals(2, backend.listings)
    }

    // El arranque en frío pide la pasada por sesión y por onStart: es una sola
    @Test
    fun coldStartAndOnStartTogetherSyncOnce() = runTest(dispatcher) {
        val sync = syncWithClock(FakeAccount(session))
        sync.requestIfStale()
        advanceUntilIdle()
        assertEquals(1, backend.listings)
    }

    // Una pasada sin conexión no consume el TTL: al volver a la app se reintenta
    @Test
    fun aFailedPassDoesNotConsumeTheTtl() = runTest(dispatcher) {
        backend.offline = true
        val sync = syncWithClock(FakeAccount(session))
        advanceUntilIdle()
        clock += 1_000

        backend.offline = false
        sync.requestIfStale()
        advanceUntilIdle()

        assertEquals(2, backend.listings)
        assertNull(sync.state.value.issue)
    }

    // Los pedidos explícitos (importar, reintentar) ignoran el TTL
    @Test
    fun explicitRequestsIgnoreTheTtl() = runTest(dispatcher) {
        val sync = syncWithClock(FakeAccount(session))
        advanceUntilIdle()
        clock += 1_000

        sync.request()
        advanceUntilIdle()

        assertEquals(2, backend.listings)
    }

    // Un pedido por antigüedad que llega después de uno explícito no lo pisa: lo importado igual se sincroniza
    @Test
    fun aStalenessRequestDoesNotSwallowAnExplicitOne() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>()
        backend.listGate = gate
        val sync = syncWithClock(FakeAccount(session))
        advanceUntilIdle()
        assertTrue(sync.state.value.running)

        sync.request()
        sync.requestIfStale()
        backend.listGate = null
        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(2, backend.listings)
    }

    // SYN-001: un proceso nuevo siempre sincroniza al arrancar, aunque otro haya sincronizado hace segundos.
    // El estado del TTL no se guarda entre procesos: una instancia nueva de LibrarySync lo simula.
    @Test
    fun aNewProcessAlwaysSyncsEvenRightAfterAnotherOne() = runTest(dispatcher) {
        syncWithClock(FakeAccount(session))
        advanceUntilIdle()
        assertEquals(1, backend.listings)
        clock += 1_000

        val newProcess = syncWithClock(FakeAccount(session))
        newProcess.requestIfStale()
        advanceUntilIdle()

        assertEquals(2, backend.listings)
    }
}
