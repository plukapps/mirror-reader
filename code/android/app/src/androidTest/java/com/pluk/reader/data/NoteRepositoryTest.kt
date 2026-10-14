package com.pluk.reader.data

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pluk.reader.data.local.db.ReaderDatabase
import com.pluk.reader.data.repository.NoteRepositoryImpl
import com.pluk.reader.data.local.db.BookEntity
import com.pluk.reader.domain.model.Note
import com.pluk.reader.domain.repository.SyncNote
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NoteRepositoryTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var db: ReaderDatabase
    private lateinit var repository: NoteRepositoryImpl

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, ReaderDatabase::class.java).build()
        repository = NoteRepositoryImpl(db.noteDao())
    }

    @After
    fun tearDown() = db.close()

    private fun note(id: String, bookId: String = "libro", progression: Double = 0.5, createdAt: Long = 1) =
        Note(id, bookId, """{"href":"c.xhtml"}""", "texto citado $id", "nota $id", progression, createdAt, createdAt)

    private fun book(id: String, uploadedAt: Long? = null) =
        BookEntity(id = id, title = "T", author = null, hasCover = false, addedAt = 0, sizeBytes = 1, uploadedAt = uploadedAt)

    // ANN-006: la nota guarda el lugar y el texto citado
    @Test
    fun noteRoundTrips() = runBlocking {
        repository.save(note("n1"))
        assertEquals(note("n1"), repository.get("n1"))
    }

    // ANN-010: por libro y en el orden del libro
    @Test
    fun observesNotesOfTheBookInBookOrder() = runBlocking {
        repository.save(note("tarde", progression = 0.8))
        repository.save(note("otro", bookId = "otro-libro", progression = 0.1))
        repository.save(note("temprano", progression = 0.2))
        repository.save(note("igual-nueva", progression = 0.8, createdAt = 5))
        assertEquals(listOf("temprano", "tarde", "igual-nueva"), repository.observe("libro").first().map { it.id })
    }

    // ANN-005: editar reemplaza la nota con el mismo id; borrar la quita
    @Test
    fun editsAndDeletes() = runBlocking {
        repository.save(note("n1"))
        repository.save(note("n1").copy(text = "editada", updatedAt = 9))
        assertEquals("editada", repository.get("n1")?.text)
        repository.delete("n1")
        assertNull(repository.get("n1"))
    }

    // ANN-011, SYN-007: al quitar el libro, las notas que la nube nunca tuvo se eliminan y las demás se marcan borradas
    @Test
    fun removingABookDeletesOrMarksItsNotes() = runBlocking {
        repository.save(note("local"))
        repository.save(note("subida"))
        repository.markSynced("subida", 1)
        repository.save(note("otra", bookId = "otro-libro"))
        db.noteDao().deleteNeverSyncedOfBook("libro")
        db.noteDao().markDeletedOfBook("libro", 50)
        assertEquals(emptyList<Note>(), repository.observe("libro").first())
        assertNull(db.noteDao().find("local"))
        assertEquals(50L, db.noteDao().find("subida")?.deletedAt)
        assertEquals(listOf("otra"), repository.observe("otro-libro").first().map { it.id })
    }

    // SYN-007: borrar una nota que la nube nunca tuvo la elimina; una subida queda marcada y pendiente
    @Test
    fun deletingMarksOnlyNotesTheCloudHas() = runBlocking {
        repository.save(note("local"))
        repository.delete("local")
        assertNull(db.noteDao().find("local"))
        repository.save(note("subida"))
        repository.markSynced("subida", 1)
        repository.delete("subida")
        assertNull(repository.get("subida"))
        val marked = db.noteDao().find("subida")
        assertTrue(marked?.deletedAt != null)
        assertTrue(marked!!.syncedAt!! < marked.updatedAt)
    }

    // SYN-016: pendientes solo si el libro está en la nube (las borradas siempre); subir las marca enviadas
    @Test
    fun pendingNeedsTheBookInTheCloud() = runBlocking {
        db.bookDao().insert(book("libro", uploadedAt = 1))
        repository.save(note("en-la-nube"))
        repository.save(note("sin-libro", bookId = "otro-libro"))
        assertEquals(listOf("en-la-nube"), repository.pending().map { it.note.id })
        repository.markSynced("en-la-nube", 1)
        assertEquals(emptyList<String>(), repository.pending().map { it.note.id })
        assertTrue(repository.hasUnsynced(syncOn = true)) // "sin-libro" todavía no subió
    }

    // SYN-005: la nota de la nube se aplica solo si es más reciente; una edición local más nueva no se pisa
    @Test
    fun appliesOnlyNewerRemoteNotes() = runBlocking {
        repository.save(note("n1").copy(text = "local", updatedAt = 10))
        repository.applyRemote(SyncNote(note("n1").copy(text = "vieja", updatedAt = 5)))
        assertEquals("local", repository.get("n1")?.text)
        repository.applyRemote(SyncNote(note("n1").copy(text = "nueva", updatedAt = 20)))
        assertEquals("nueva", repository.get("n1")?.text)
        assertEquals(emptyList<String>(), repository.pending().map { it.note.id }) // vino de la nube: ya está allí
        repository.applyRemote(SyncNote(note("n2").copy(updatedAt = 3)))
        assertEquals("nota n2", repository.get("n2")?.text)
    }

    // SYN-007: un borrado de la nube más reciente elimina la nota; uno más viejo que una edición local, no
    @Test
    fun appliesRemoteDeletions() = runBlocking {
        repository.save(note("n1").copy(updatedAt = 10))
        repository.applyRemote(SyncNote(note("n1").copy(updatedAt = 5), deletedAt = 5))
        assertEquals("nota n1", repository.get("n1")?.text)
        repository.applyRemote(SyncNote(note("n1").copy(updatedAt = 30), deletedAt = 30))
        assertNull(db.noteDao().find("n1"))
    }

    // PRF-009: con la sincronización apagada, cualquier nota viva se perdería al cerrar sesión
    @Test
    fun unsyncedDependsOnTheSwitch() = runBlocking {
        repository.save(note("n1"))
        repository.markSynced("n1", 1)
        assertFalse(repository.hasUnsynced(syncOn = true))
        assertTrue(repository.hasUnsynced(syncOn = false))
    }
}
