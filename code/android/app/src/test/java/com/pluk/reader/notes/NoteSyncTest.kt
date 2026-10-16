package com.pluk.reader.notes

import com.pluk.reader.domain.account.AccountUser
import com.pluk.reader.domain.flags.Feature
import com.pluk.reader.domain.flags.FeatureFlags
import com.pluk.reader.domain.remote.NoteSyncCursor
import com.pluk.reader.domain.remote.RemoteNoteChanges
import com.pluk.reader.domain.remote.RemoteNotes
import com.pluk.reader.domain.remote.RemoteUnavailableException
import com.pluk.reader.domain.repository.NoteSyncStore
import com.pluk.reader.domain.repository.SyncNote
import com.pluk.reader.domain.sync.SyncGate
import com.pluk.reader.domain.usecase.NoteSync
import com.pluk.reader.remote.FakeSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NoteSyncTest {
    /** Base local con las mismas reglas que `NoteDao` (SYN-005, SYN-007). */
    private class FakeStore : NoteSyncStore {
        data class Row(val note: SyncNote, val syncedAt: Long?)

        val rows = mutableMapOf<String, Row>()
        val booksInCloud = mutableSetOf("hash-del-libro")

        fun local(id: String, editedAt: Long, syncedAt: Long? = null, deletedAt: Long? = null, bookId: String = "hash-del-libro") {
            rows[id] = Row(SyncNote(note(id, bookId).copy(updatedAt = editedAt), deletedAt), syncedAt)
        }

        override suspend fun pending() = rows.values
            .filter { (it.syncedAt ?: -1) < it.note.editedAt }
            .filter { it.note.deletedAt != null || it.note.note.bookId in booksInCloud }
            .map { it.note }

        override suspend fun hasUnsynced(syncOn: Boolean) = rows.values.any { (it.syncedAt ?: -1) < it.note.editedAt }

        override suspend fun markSynced(id: String, editedAt: Long) {
            rows[id]?.takeIf { it.note.editedAt == editedAt }?.let { rows[id] = it.copy(syncedAt = editedAt) }
        }

        override suspend fun applyRemote(remote: SyncNote) {
            val current = rows[remote.note.id]
            if (remote.deletedAt != null) {
                if (current != null && current.note.editedAt <= remote.editedAt) rows.remove(remote.note.id)
                return
            }
            if (current == null || current.note.editedAt < remote.editedAt) rows[remote.note.id] = Row(remote, remote.editedAt)
        }

        override suspend fun purgeSyncedDeletions() {
            rows.values.removeAll { it.note.deletedAt != null && (it.syncedAt ?: -1) >= it.note.editedAt }
        }
    }

    /** La nube, con las reglas de `firestore.rules`: gana la edición más reciente. */
    private class FakeRemote : RemoteNotes {
        val cloud = mutableMapOf<String, SyncNote>()
        val changes = MutableSharedFlow<Result<RemoteNoteChanges>>(extraBufferCapacity = 8)
        var offline = false
        var rejectBookIds = emptySet<String>()
        var pushes = 0
        var listenedSince: Long? = -1

        override suspend fun push(note: SyncNote): Result<Unit> {
            pushes++
            if (offline) return Result.failure(RemoteUnavailableException("sin red"))
            if (note.note.bookId in rejectBookIds) return Result.failure(IllegalStateException("PERMISSION_DENIED"))
            val existing = cloud[note.note.id]
            if (existing == null || existing.editedAt <= note.editedAt) cloud[note.note.id] = note
            return Result.success(Unit)
        }

        override fun observeChanges(sinceMillis: Long?): Flow<Result<RemoteNoteChanges>> {
            listenedSince = sinceMillis
            return changes
        }
    }

    private class FakeFlags(on: Boolean) : FeatureFlags {
        val notesSync = MutableStateFlow(on)
        override fun state(feature: Feature): StateFlow<Boolean> = notesSync
    }

    private class FakeGate(open: Boolean = true) : SyncGate {
        override val open = MutableStateFlow(open)
        override suspend fun isOpen() = open.value
    }

    private class FakeCursor : NoteSyncCursor {
        var value: Long? = null
        override suspend fun lastSeenAt() = value
        override suspend fun setLastSeenAt(value: Long) {
            this.value = value
        }
    }

    private val dispatcher = StandardTestDispatcher()
    private val store = FakeStore()
    private val remote = FakeRemote()
    private val cursor = FakeCursor()
    private val flags = FakeFlags(on = true)
    private val gate = FakeGate()
    private val session = FakeSession(AccountUser("u1", null))

    private fun sync() = NoteSync(remote, store, cursor, flags, session, CoroutineScope(dispatcher), gate)

    private fun idle() = dispatcher.scheduler.advanceUntilIdle()

    // SYN-016: las pendientes suben y quedan marcadas; las que no pudieron, siguen pendientes
    @Test
    fun uploadsPendingNotes() = runTest(dispatcher) {
        store.local("a", editedAt = 10)
        store.local("b", editedAt = 20, bookId = "otro-libro")
        store.booksInCloud += "otro-libro"
        remote.rejectBookIds = setOf("otro-libro")
        sync().flush()
        assertEquals(10L, remote.cloud.getValue("a").editedAt)
        assertEquals(listOf("b"), store.pending().map { it.note.id })
    }

    // SYN-016: una nota de un libro que todavía no está en la nube espera
    @Test
    fun waitsForTheBookToBeInTheCloud() = runTest(dispatcher) {
        store.local("a", editedAt = 10, bookId = "sin-subir")
        sync().flush()
        assertEquals(0, remote.pushes)
    }

    // SYN-001: sin conexión corta y deja todo pendiente
    @Test
    fun stopsWhenOffline() = runTest(dispatcher) {
        store.local("a", editedAt = 10)
        store.local("b", editedAt = 11)
        remote.offline = true
        sync().flush()
        assertEquals(1, remote.pushes)
        assertEquals(2, store.pending().size)
    }

    // SYN-007: un borrado sube y después la nota se elimina de la base
    @Test
    fun uploadsDeletionsAndPurgesThem() = runTest(dispatcher) {
        store.local("a", editedAt = 30, syncedAt = 10, deletedAt = 30)
        sync().flush()
        assertEquals(30L, remote.cloud.getValue("a").deletedAt)
        assertNull(store.rows["a"])
    }

    // SYN-017: apagada, no se sube nada; PRF-023: tampoco sin Wi-Fi
    @Test
    fun doesNothingWhenOffOrGateClosed() = runTest(dispatcher) {
        store.local("a", editedAt = 10)
        flags.notesSync.value = false
        sync().flush()
        flags.notesSync.value = true
        gate.open.value = false
        sync().flush()
        assertEquals(0, remote.pushes)
    }

    // SYN-016, SYN-005: con la app a la vista llegan las notas de otros dispositivos; gana la edición más reciente
    @Test
    fun appliesRemoteChangesWhileListening() = runTest(dispatcher) {
        store.local("mia", editedAt = 50, syncedAt = 50)
        val sync = sync()
        sync.startListening()
        idle()
        val nueva = SyncNote(note("nueva").copy(updatedAt = 40))
        val vieja = SyncNote(note("mia").copy(text = "vieja", updatedAt = 20))
        remote.changes.emit(Result.success(RemoteNoteChanges(listOf(nueva, vieja), newestUpdatedAt = 900)))
        idle()
        assertEquals("nota nueva", store.rows.getValue("nueva").note.note.text)
        assertEquals("nota mia", store.rows.getValue("mia").note.note.text)
        assertEquals(900L, cursor.value)
        sync.stopListening()
    }

    // SYN-007: un borrado de otro dispositivo quita la nota
    @Test
    fun appliesRemoteDeletions() = runTest(dispatcher) {
        store.local("a", editedAt = 10, syncedAt = 10)
        val sync = sync()
        sync.startListening()
        idle()
        remote.changes.emit(Result.success(RemoteNoteChanges(listOf(SyncNote(note("a").copy(updatedAt = 30), 30)), 1000)))
        idle()
        assertNull(store.rows["a"])
        sync.stopListening()
    }

    // SYN-017: apagada no escucha; al prenderse sube lo pendiente y escucha desde la última vez
    @Test
    fun turningOnUploadsAndListens() = runTest(dispatcher) {
        flags.notesSync.value = false
        cursor.value = 700
        store.local("a", editedAt = 10)
        val sync = sync()
        sync.startListening()
        idle()
        assertEquals(-1L, remote.listenedSince) // no escuchó
        assertEquals(0, remote.pushes)
        flags.notesSync.value = true
        idle()
        assertEquals(10L, remote.cloud.getValue("a").editedAt)
        assertEquals(700L, remote.listenedSince)
        sync.stopListening()
    }

    // SYN-017: apagarla con la app a la vista deja de aplicar cambios de la nube; nunca borra nada
    @Test
    fun turningOffStopsApplyingChanges() = runTest(dispatcher) {
        val sync = sync()
        sync.startListening()
        idle()
        flags.notesSync.value = false
        idle()
        remote.changes.emit(Result.success(RemoteNoteChanges(listOf(SyncNote(note("x").copy(updatedAt = 5))), 10)))
        idle()
        assertTrue(store.rows.isEmpty())
        sync.stopListening()
    }
}
