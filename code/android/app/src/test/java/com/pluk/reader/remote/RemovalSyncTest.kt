package com.pluk.reader.remote

import com.pluk.reader.domain.account.AccountUser
import com.pluk.reader.domain.remote.RemoteRemovals
import com.pluk.reader.domain.remote.RemoteUnavailableException
import com.pluk.reader.domain.repository.BookRemovals
import com.pluk.reader.domain.usecase.ReadingSession
import com.pluk.reader.domain.usecase.RemovalSync
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** LIB-020, LIB-008: quitar un libro lo borra de la nube y de los otros dispositivos. */
@OptIn(ExperimentalCoroutinesApi::class)
class RemovalSyncTest {
    private val dispatcher = StandardTestDispatcher()

    private class FakeRemote : RemoteRemovals {
        val calls = mutableListOf<String>()
        var offline = false
        var failFilesFor = mutableSetOf<String>()
        var deleted = listOf<String>()
        val changes = MutableSharedFlow<Result<List<String>>>(extraBufferCapacity = 4)

        override suspend fun markDeleted(bookId: String): Result<Unit> {
            if (offline) return Result.failure(RemoteUnavailableException())
            calls += "marcar:$bookId"
            return Result.success(Unit)
        }

        override suspend fun deleteFiles(bookId: String): Result<Unit> {
            if (offline || bookId in failFilesFor) return Result.failure(RemoteUnavailableException())
            calls += "archivos:$bookId"
            return Result.success(Unit)
        }

        override suspend fun deletedBookIds(): Result<List<String>> =
            if (offline) Result.failure(RemoteUnavailableException()) else Result.success(deleted)

        override fun observeDeleted(): Flow<Result<List<String>>> = changes
    }

    private class FakeLocal : BookRemovals {
        val pending = mutableListOf<String>()
        val removedByCloud = mutableListOf<String>()
        override suspend fun pendingCloudRemovals(): List<String> = pending.toList()
        override suspend fun markCloudRemoved(bookId: String) {
            pending -= bookId
        }
        override suspend fun removeDeletedInCloud(bookIds: List<String>) {
            removedByCloud += bookIds
        }
    }

    private val remote = FakeRemote()
    private val local = FakeLocal()
    private val session = ReadingSession()
    private val account = FakeSession(AccountUser("u1", null))

    private fun removals() = RemovalSync(remote, local, session, account, CoroutineScope(dispatcher))

    // LIB-020: primero se marca en la nube, después se borran los archivos y recién ahí queda hecho
    @Test
    fun pushMarksDeletedThenDeletesFiles() = runTest(dispatcher) {
        local.pending += listOf("a", "b")
        val sync = removals()

        assertTrue(sync.sync().isSuccess)

        assertEquals(listOf("marcar:a", "archivos:a", "marcar:b", "archivos:b"), remote.calls)
        assertTrue(local.pending.isEmpty())
    }

    // LIB-020: sin conexión queda pendiente y la pasada lo informa
    @Test
    fun offlineKeepsPending() = runTest(dispatcher) {
        local.pending += "a"
        remote.offline = true
        val sync = removals()

        val result = sync.sync()

        assertTrue(result.exceptionOrNull() is RemoteUnavailableException)
        assertEquals(listOf("a"), local.pending)
    }

    // LIB-020: si fallan los archivos, se reintenta todo la próxima vez (marcar de nuevo no hace daño)
    @Test
    fun failedFilesStayPending() = runTest(dispatcher) {
        local.pending += "a"
        remote.failFilesFor += "a"
        val sync = removals()

        sync.sync()

        assertEquals(listOf("a"), local.pending)
    }

    // LIB-008: al sincronizar se quitan aquí los libros borrados en otro dispositivo
    @Test
    fun pullRemovesBooksDeletedElsewhere() = runTest(dispatcher) {
        remote.deleted = listOf("x", "y")
        val sync = removals()

        sync.sync()

        assertEquals(listOf("x", "y"), local.removedByCloud)
    }

    // LIB-020: un libro abierto en el lector se quita al cerrarlo
    @Test
    fun openBookIsRemovedWhenClosed() = runTest(dispatcher) {
        remote.deleted = listOf("abierto", "otro")
        session.opened("abierto")
        val sync = removals()

        sync.sync()
        assertEquals(listOf("otro"), local.removedByCloud)

        session.closed("abierto")
        advanceUntilIdle()
        assertEquals(listOf("otro", "abierto"), local.removedByCloud)
    }

    // LIB-020: con la app a la vista, un borrado de otro dispositivo llega sin sincronizar
    @Test
    fun listeningRemovesAsSoonAsItArrives() = runTest(dispatcher) {
        val sync = removals()
        sync.startListening()
        advanceUntilIdle()

        remote.changes.emit(Result.success(listOf("z")))
        advanceUntilIdle()
        assertEquals(listOf("z"), local.removedByCloud)

        sync.stopListening()
        remote.changes.emit(Result.success(listOf("w")))
        advanceUntilIdle()
        assertEquals(listOf("z"), local.removedByCloud)
    }

    // Sin sesión no hay nube: no se envía ni se consulta nada
    @Test
    fun withoutSessionDoesNothing() = runTest(dispatcher) {
        local.pending += "a"
        account.state.value = null
        val sync = removals()

        assertTrue(sync.sync().isSuccess)
        assertTrue(remote.calls.isEmpty())
        assertEquals(listOf("a"), local.pending)
    }

    // LIB-020: quitar pide enviar al momento
    @Test
    fun requestPushSendsInBackground() = runTest(dispatcher) {
        local.pending += "a"
        val sync = removals()

        sync.requestPush()
        advanceUntilIdle()

        assertEquals(listOf("marcar:a", "archivos:a"), remote.calls)
    }
}
