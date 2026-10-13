package com.pluk.reader.remote

import com.pluk.reader.domain.account.AccountUser
import com.pluk.reader.domain.remote.RemoteBookChanges
import com.pluk.reader.domain.repository.KnownBooks
import com.pluk.reader.domain.usecase.LibraryWatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/** SYN-015: con la app a la vista, un libro agregado en otro dispositivo pide una pasada de la biblioteca. */
@OptIn(ExperimentalCoroutinesApi::class)
class LibraryWatcherTest {
    private val dispatcher = StandardTestDispatcher()

    private class FakeChanges : RemoteBookChanges {
        val since = mutableListOf<Long>()
        val changes = MutableSharedFlow<Result<List<String>>>(extraBufferCapacity = 4)
        override fun observeAliveSince(sinceMillis: Long): Flow<Result<List<String>>> {
            since += sinceMillis
            return changes
        }
    }

    private val remote = FakeChanges()
    private val known = mutableSetOf("ya")
    private var requests = 0
    private val account = FakeSession(AccountUser("u1", null))

    private val gate = FakeGate()

    private fun watcher() = LibraryWatcher(
        remote,
        KnownBooks { known.toSet() },
        { requests++ },
        account,
        CoroutineScope(dispatcher),
        now = { 100_000_000L },
        gate = gate,
    )

    @Test
    fun unknownBookRequestsAPass() = runTest(dispatcher) {
        val watcher = watcher()
        watcher.startListening()
        advanceUntilIdle()

        remote.changes.emit(Result.success(listOf("ya")))
        advanceUntilIdle()
        assertEquals(0, requests)

        remote.changes.emit(Result.success(listOf("ya", "nuevo")))
        advanceUntilIdle()
        assertEquals(1, requests)
    }

    // Solo lo cambiado hace poco: no se relee toda la biblioteca
    @Test
    fun listensFrom15MinutesAgo() = runTest(dispatcher) {
        watcher().startListening()
        advanceUntilIdle()
        assertEquals(listOf(100_000_000L - 15 * 60_000L), remote.since)
    }

    @Test
    fun stopsListening() = runTest(dispatcher) {
        val watcher = watcher()
        watcher.startListening()
        advanceUntilIdle()
        watcher.stopListening()

        remote.changes.emit(Result.success(listOf("nuevo")))
        advanceUntilIdle()
        assertEquals(0, requests)
    }

    @Test
    fun withoutSessionDoesNotListen() = runTest(dispatcher) {
        account.state.value = null
        watcher().startListening()
        advanceUntilIdle()
        assertEquals(emptyList<Long>(), remote.since)
    }

    // PRF-023: sin Wi-Fi no se escuchan los libros nuevos de otros dispositivos
    @Test
    fun withoutWifiDoesNotWatch() = runTest(dispatcher) {
        gate.state.value = false
        val watcher = watcher()
        watcher.startListening()
        advanceUntilIdle()
        assertEquals(0, remote.since.size)
        watcher.stopListening()
    }
}
