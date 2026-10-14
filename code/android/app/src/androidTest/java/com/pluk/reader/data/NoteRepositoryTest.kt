package com.pluk.reader.data

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pluk.reader.data.local.db.ReaderDatabase
import com.pluk.reader.data.repository.NoteRepositoryImpl
import com.pluk.reader.domain.model.Note
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
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

    // ANN-011: borrar por libro deja las notas de los demás
    @Test
    fun deleteByBookKeepsOtherBooks() = runBlocking {
        repository.save(note("a"))
        repository.save(note("b", bookId = "otro-libro"))
        db.noteDao().deleteByBook("libro")
        assertEquals(emptyList<Note>(), repository.observe("libro").first())
        assertEquals(listOf("b"), repository.observe("otro-libro").first().map { it.id })
    }
}
