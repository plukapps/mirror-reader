package com.pluk.reader.remote

import com.pluk.reader.domain.account.AccountUser
import com.pluk.reader.domain.model.RemotePosition
import com.pluk.reader.domain.remote.RemoteChanges
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** SYN-003, SYN-011, SYN-012, SYN-013: la posición sale al cerrar el libro y llega sola mientras la app se ve. */
@OptIn(ExperimentalCoroutinesApi::class)
class PositionSyncTest {
    private val dispatcher = StandardTestDispatcher()
    private val backend = FakePositionBackend()
    private val session = AccountUser("u1", null)

    private fun sync(account: FakeSession = FakeSession(session)) = backend.sync(account, CoroutineScope(dispatcher))

    private fun changes(vararg positions: RemotePosition, newest: Long? = 1_000L) =
        Result.success(RemoteChanges(positions.toList(), newest))

    // SYN-011: lo pendiente sale, con el identificador y el nombre de este dispositivo
    @Test
    fun flushSendsThePendingPositionsAndMarksThemSynced() = runTest(dispatcher) {
        backend.savedLocally("a", readAt = 10)
        backend.savedLocally("b", readAt = 20, synced = true)

        sync().flush()

        assertEquals(listOf("enviar:a"), backend.calls)
        assertEquals(Triple(backend.position("a", 10), "este-dispositivo", "SM-S711B"), backend.pushes.single())
        assertTrue(backend.local.getValue("a").isSynced)
    }

    @Test
    fun flushWithNothingPendingDoesNotTouchTheCloud() = runTest(dispatcher) {
        backend.savedLocally("a", readAt = 10, synced = true)
        sync().flush()
        assertTrue(backend.pushes.isEmpty())
    }

    // SYN-011: pasar de página no envía nada
    @Test
    fun savingLocallyDoesNotSendAnything() = runTest(dispatcher) {
        sync()
        backend.savedLocally("a", readAt = 10)
        advanceUntilIdle()
        assertTrue(backend.pushes.isEmpty())
    }

    // SYN-001: sin conexión queda pendiente y no se intenta con el resto
    @Test
    fun offlineLeavesEverythingPendingAndStopsAtTheFirst() = runTest(dispatcher) {
        backend.offline = true
        backend.savedLocally("a", readAt = 10)
        backend.savedLocally("b", readAt = 20)

        sync().flush()

        assertEquals(setOf("a", "b"), backend.pending().map { it.bookId }.toSet())
        assertTrue(backend.pushes.isEmpty())
        // Sin conexión no tiene sentido intentar con el resto
        assertEquals(1, backend.pushAttempts)
    }

    // Un rechazo (por ejemplo el libro aún no está en la nube) no frena a los demás y queda pendiente
    @Test
    fun aRejectedPositionStaysPendingAndDoesNotStopTheOthers() = runTest(dispatcher) {
        backend.rejection = IllegalStateException("permission-denied")
        backend.savedLocally("a", readAt = 10)
        val sync = sync()

        sync.flush()
        assertEquals(listOf("a"), backend.pending().map { it.bookId })

        backend.rejection = null
        sync.flush()
        assertTrue(backend.pending().isEmpty())
    }

    @Test
    fun flushDoesNothingWithoutASession() = runTest(dispatcher) {
        backend.savedLocally("a", readAt = 10)
        sync(FakeSession(null)).flush()
        assertTrue(backend.pushes.isEmpty())
        assertEquals(listOf("a"), backend.pending().map { it.bookId })
    }

    // Nunca corren dos envíos a la vez
    @Test
    fun twoFlushesAtTheSameTimeSendOnce() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>()
        backend.pushGate = gate
        backend.savedLocally("a", readAt = 10)
        val sync = sync()

        val first = launch { sync.flush() }
        val second = launch { sync.flush() }
        advanceUntilIdle()
        gate.complete(Unit)
        first.join()
        second.join()

        assertEquals(1, backend.pushes.size)
    }

    // SYN-011: si se leyó más mientras se enviaba, la lectura nueva sigue pendiente
    @Test
    fun readingMoreWhileSendingKeepsTheNewerReadingPending() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>()
        backend.pushGate = gate
        backend.savedLocally("a", readAt = 10)
        val sync = sync()

        val flushing = launch { sync.flush() }
        advanceUntilIdle()
        backend.savedLocally("a", readAt = 20)
        gate.complete(Unit)
        flushing.join()

        assertFalse(backend.local.getValue("a").isSynced)
        assertEquals(20L, backend.local.getValue("a").position.readAt)
    }

    // SYN-011: al cerrar el libro se guarda la última posición y recién entonces se envía
    @Test
    fun closingTheBookSavesTheLastPositionAndThenSends() = runTest(dispatcher) {
        val sync = sync()

        sync.onBookClosed {
            backend.calls += "guardar"
            backend.savedLocally("a", readAt = 30)
        }
        advanceUntilIdle()

        assertEquals(listOf("guardar", "enviar:a"), backend.calls)
    }

    // SYN-012: una lectura más nueva de otro dispositivo se guarda aquí y se avisa (SYN-013)
    @Test
    fun aNewerRemoteReadingIsAppliedAndAnnounced() = runTest(dispatcher) {
        backend.savedLocally("a", readAt = 10, synced = true)
        val sync = sync()
        val announced = mutableListOf<RemotePosition>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { sync.remoteUpdates.collect { announced += it } }
        sync.startListening()
        advanceUntilIdle()

        backend.changes.emit(changes(backend.remotePosition("a", readAt = 50, locator = "nueva")))
        advanceUntilIdle()

        assertEquals("nueva", backend.local.getValue("a").position.locatorJson)
        assertTrue(backend.local.getValue("a").isSynced)
        assertEquals(listOf("a"), announced.map { it.position.bookId })
        assertEquals(1_000L, backend.seenAt)
    }

    // El eco de lo que este mismo dispositivo envió no se anuncia ni se vuelve a aplicar
    @Test
    fun theEchoOfThisDevicesOwnWriteIsNotAnnounced() = runTest(dispatcher) {
        backend.savedLocally("a", readAt = 10, synced = false)
        val sync = sync()
        val announced = mutableListOf<RemotePosition>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { sync.remoteUpdates.collect { announced += it } }
        sync.startListening()
        advanceUntilIdle()

        backend.changes.emit(changes(backend.remotePosition("a", readAt = 10, device = "este-dispositivo")))
        advanceUntilIdle()

        assertTrue(announced.isEmpty())
        assertTrue(backend.local.getValue("a").isSynced)
    }

    // Reinstalar: la nube tiene una lectura nuestra anterior; se aplica pero no se anuncia como de "otro dispositivo"
    @Test
    fun aNewerReadingFromThisSameDeviceIsAppliedWithoutAnnouncing() = runTest(dispatcher) {
        backend.savedLocally("a", readAt = 10, synced = true)
        val sync = sync()
        val announced = mutableListOf<RemotePosition>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { sync.remoteUpdates.collect { announced += it } }
        sync.startListening()
        advanceUntilIdle()

        backend.changes.emit(changes(backend.remotePosition("a", readAt = 40, device = "este-dispositivo")))
        advanceUntilIdle()

        assertEquals(40L, backend.local.getValue("a").position.readAt)
        assertTrue(announced.isEmpty())
    }

    // SYN-003, SYN-010: una lectura local más nueva no se pisa; se envía
    @Test
    fun aNewerLocalReadingIsKeptAndSent() = runTest(dispatcher) {
        backend.savedLocally("a", readAt = 90, locator = "local")
        val sync = sync()
        sync.startListening()
        advanceUntilIdle()

        backend.changes.emit(changes(backend.remotePosition("a", readAt = 50, locator = "remota")))
        advanceUntilIdle()

        assertEquals("local", backend.local.getValue("a").position.locatorJson)
        assertEquals(listOf("enviar:a"), backend.calls)
    }

    // Estado incoherente: marcada como enviada pero la nube está atrás
    @Test
    fun aLocalReadingMarkedSyncedButAheadOfTheCloudIsSentAgain() = runTest(dispatcher) {
        backend.savedLocally("a", readAt = 90, synced = true)
        val sync = sync()
        sync.startListening()
        advanceUntilIdle()

        backend.changes.emit(changes(backend.remotePosition("a", readAt = 50)))
        advanceUntilIdle()

        assertEquals(listOf("enviar:a"), backend.calls)
        assertTrue(backend.local.getValue("a").isSynced)
    }

    // SYN-012: se pide desde donde se quedó la última vez
    @Test
    fun listeningStartsFromTheLastSeenInstant() = runTest(dispatcher) {
        backend.seenAt = 777L
        sync().startListening()
        advanceUntilIdle()
        assertEquals(listOf<Long?>(777L), backend.observedSince)
    }

    @Test
    fun theFirstTimeEverythingIsRequested() = runTest(dispatcher) {
        sync().startListening()
        advanceUntilIdle()
        assertEquals(listOf<Long?>(null), backend.observedSince)
    }

    @Test
    fun startingTwiceListensOnce() = runTest(dispatcher) {
        val sync = sync()
        sync.startListening()
        sync.startListening()
        advanceUntilIdle()
        assertEquals(1, backend.observedSince.size)
    }

    // Sin trabajo en segundo plano: al dejar de verse la app se deja de escuchar
    @Test
    fun stoppingStopsApplyingChanges() = runTest(dispatcher) {
        backend.savedLocally("a", readAt = 10, synced = true)
        val sync = sync()
        sync.startListening()
        advanceUntilIdle()
        sync.stopListening()
        advanceUntilIdle()

        backend.changes.emit(changes(backend.remotePosition("a", readAt = 50)))
        advanceUntilIdle()

        assertEquals(10L, backend.local.getValue("a").position.readAt)
    }

    @Test
    fun listeningCanStartAgainAfterStopping() = runTest(dispatcher) {
        val sync = sync()
        sync.startListening()
        advanceUntilIdle()
        sync.stopListening()
        sync.startListening()
        advanceUntilIdle()
        assertEquals(2, backend.observedSince.size)
    }

    // La sesión llega después de arrancar: la escucha empieza entonces
    @Test
    fun listeningWaitsForTheSession() = runTest(dispatcher) {
        val account = FakeSession(null)
        val sync = sync(account)
        sync.startListening()
        advanceUntilIdle()
        assertTrue(backend.observedSince.isEmpty())

        account.state.value = session
        advanceUntilIdle()

        assertEquals(1, backend.observedSince.size)
    }

    // El instante visto nunca retrocede
    @Test
    fun theLastSeenInstantNeverGoesBackwards() = runTest(dispatcher) {
        backend.seenAt = 5_000L
        val sync = sync()
        sync.startListening()
        advanceUntilIdle()

        backend.changes.emit(changes(backend.remotePosition("a", readAt = 50), newest = 2_000L))
        advanceUntilIdle()

        assertEquals(5_000L, backend.seenAt)
    }

    @Test
    fun noRemoteChangeLeavesTheLastSeenInstantAlone() = runTest(dispatcher) {
        val sync = sync()
        sync.startListening()
        advanceUntilIdle()
        backend.changes.emit(changes(newest = null))
        advanceUntilIdle()
        assertNull(backend.seenAt)
    }
}
