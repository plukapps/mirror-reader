package com.pluk.reader.remote

import com.pluk.reader.domain.account.AccountUser
import com.pluk.reader.domain.sync.NetworkMonitor
import com.pluk.reader.domain.sync.NetworkSyncGate
import com.pluk.reader.domain.sync.NetworkType
import com.pluk.reader.domain.sync.WifiOnlyPreference
import com.pluk.reader.domain.sync.canSync
import com.pluk.reader.domain.usecase.LibrarySync
import com.pluk.reader.domain.usecase.SyncGateWatcher
import com.pluk.reader.domain.usecase.SyncIssue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** PRF-023: "Sincronizar solo con Wi-Fi". */
@OptIn(ExperimentalCoroutinesApi::class)
class WifiOnlySyncTest {
    private val dispatcher = StandardTestDispatcher()
    private val backend = FakeSyncBackend()
    private val gate = FakeGate()
    private val session = AccountUser("u1", null)

    private class FakePreference(initial: Boolean) : WifiOnlyPreference {
        override val wifiOnly = MutableStateFlow(initial)
        override suspend fun setWifiOnly(enabled: Boolean) {
            wifiOnly.value = enabled
        }
    }

    private class FakeNetwork(initial: NetworkType) : NetworkMonitor {
        override val network = MutableStateFlow(initial)
    }

    // PRF-023: apagado sincroniza por cualquier red; encendido, solo por Wi-Fi o cable
    @Test
    fun gateOpensOnlyOnWifiWhenWifiOnly() {
        assertTrue(canSync(wifiOnly = false, NetworkType.OTHER))
        assertTrue(canSync(wifiOnly = false, NetworkType.NONE))
        assertTrue(canSync(wifiOnly = false, NetworkType.WIFI))
        assertTrue(canSync(wifiOnly = true, NetworkType.WIFI))
        assertFalse(canSync(wifiOnly = true, NetworkType.OTHER))
        assertFalse(canSync(wifiOnly = true, NetworkType.NONE))
    }

    // PRF-023: la puerta sigue la preferencia y la red
    @Test
    fun networkGateFollowsPreferenceAndNetwork() = runTest(dispatcher) {
        val pref = FakePreference(initial = true)
        val net = FakeNetwork(NetworkType.OTHER)
        val gate = NetworkSyncGate(pref, net)
        assertFalse(gate.isOpen())
        assertFalse(gate.open.first())
        net.network.value = NetworkType.WIFI
        assertTrue(gate.isOpen())
        net.network.value = NetworkType.OTHER
        pref.setWifiOnly(false)
        assertTrue(gate.isOpen())
    }

    // PRF-023: sin Wi-Fi la pasada no toca la nube y la biblioteca dice que espera el Wi-Fi
    @Test
    fun passDoesNotRunWithoutWifi() = runTest(dispatcher) {
        gate.state.value = false
        backend.pending += backend.pendingBook("a")
        val sync = librarySync()
        advanceUntilIdle()
        assertEquals(0, backend.listings)
        assertEquals(0, backend.removalSyncs)
        assertTrue(backend.uploaded.isEmpty())
        assertEquals(0, backend.positionFlushes)
        assertEquals(SyncIssue.WaitingForWifi, sync.state.value.issue)
    }

    // PRF-023: un pedido explícito (importar) tampoco corre sin Wi-Fi
    @Test
    fun explicitRequestWaitsForWifi() = runTest(dispatcher) {
        val sync = librarySync()
        advanceUntilIdle()
        gate.state.value = false
        advanceUntilIdle()
        assertEquals(SyncIssue.WaitingForWifi, sync.state.value.issue)
        sync.request()
        advanceUntilIdle()
        assertEquals(1, backend.listings)
    }

    // PRF-023: al volver el Wi-Fi con la app a la vista, se pide una pasada y se envían las posiciones
    @Test
    fun wifiArrivingRequestsAPassWhileVisible() = runTest(dispatcher) {
        gate.state.value = false
        val sync = librarySync()
        var flushes = 0
        val watcher = SyncGateWatcher(gate, sync, { flushes++ }, CoroutineScope(dispatcher))
        watcher.startListening()
        advanceUntilIdle()
        assertEquals(0, backend.listings)

        gate.state.value = true
        advanceUntilIdle()
        assertEquals(1, backend.listings)
        assertEquals(1, flushes)
        assertNull(sync.state.value.issue)
        watcher.stopListening()
    }

    // PRF-023: con la app en segundo plano no se pide nada; al volver, la pasada por antigüedad lo trae
    @Test
    fun wifiArrivingInBackgroundDoesNothing() = runTest(dispatcher) {
        gate.state.value = false
        val sync = librarySync()
        val watcher = SyncGateWatcher(gate, sync, {}, CoroutineScope(dispatcher))
        advanceUntilIdle()
        gate.state.value = true
        advanceUntilIdle()
        assertEquals(0, backend.listings)
        sync.requestIfStale()
        advanceUntilIdle()
        assertEquals(1, backend.listings)
        watcher.stopListening()
    }

    // PRF-023: apagar el interruptor (la puerta se abre) sincroniza en el momento; si ya estaba abierta, no repite
    @Test
    fun openingTheGateOnlyTriggersOnChange() = runTest(dispatcher) {
        val sync = librarySync()
        val watcher = SyncGateWatcher(gate, sync, {}, CoroutineScope(dispatcher))
        watcher.startListening()
        advanceUntilIdle()
        assertEquals(1, backend.listings) // la del arranque
        gate.state.value = true
        advanceUntilIdle()
        assertEquals(1, backend.listings)
        watcher.stopListening()
    }

    // PRF-023: sin Wi-Fi la posición de lectura queda pendiente; con Wi-Fi sale
    @Test
    fun positionsWaitForWifi() = runTest(dispatcher) {
        val positions = FakePositionBackend()
        positions.savedLocally("a", readAt = 10)
        val sync = positions.sync(FakeSession(session), CoroutineScope(dispatcher), gate)
        gate.state.value = false
        sync.flush()
        assertTrue(positions.pushes.isEmpty())
        assertFalse(positions.local.getValue("a").isSynced)
        gate.state.value = true
        sync.flush()
        assertEquals(1, positions.pushes.size)
    }

    // PRF-023: sin Wi-Fi no se reciben posiciones de otros dispositivos
    @Test
    fun positionsAreNotObservedWithoutWifi() = runTest(dispatcher) {
        val positions = FakePositionBackend()
        val sync = positions.sync(FakeSession(session), CoroutineScope(dispatcher), gate)
        gate.state.value = false
        sync.startListening()
        advanceUntilIdle()
        assertTrue(positions.observedSince.isEmpty())
        gate.state.value = true
        advanceUntilIdle()
        assertEquals(1, positions.observedSince.size)
        sync.stopListening()
    }

    private fun librarySync() =
        LibrarySync(backend.useCase(gate), FakeSession(session), CoroutineScope(dispatcher), gate = gate).also { it.start() }
}
