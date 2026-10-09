package com.pluk.reader.data

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pluk.reader.data.local.db.ReaderDatabase
import com.pluk.reader.data.repository.PositionRepositoryImpl
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReadingPositionTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var db: ReaderDatabase
    private lateinit var repository: PositionRepositoryImpl

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, ReaderDatabase::class.java).build()
        repository = PositionRepositoryImpl(db.readingPositionDao())
    }

    @After
    fun tearDown() = db.close()

    // RDR-006
    @Test
    fun positionRoundTrips() = runBlocking {
        repository.save("libro-1", """{"href":"ch2.xhtml"}""", null)
        assertEquals("""{"href":"ch2.xhtml"}""", repository.get("libro-1"))
    }

    // RDR-006: libro desconocido
    @Test
    fun unknownBookHasNoPosition() = runBlocking {
        assertNull(repository.get("no-existe"))
    }

    // RDR-006: cada libro guarda la suya
    @Test
    fun positionsAreIndependentPerBook() = runBlocking {
        repository.save("A", "a", null)
        repository.save("B", "b", null)
        assertEquals("a", repository.get("A"))
        assertEquals("b", repository.get("B"))
    }

    // RDR-006: guardar de nuevo reemplaza, no duplica
    @Test
    fun savingAgainReplacesThePosition() = runBlocking {
        repository.save("A", "primera", null)
        repository.save("A", "segunda", null)
        assertEquals("segunda", repository.get("A"))
    }

    private suspend fun uploadedBook(id: String) {
        db.bookDao().insert(com.pluk.reader.data.local.db.BookEntity(id, "T", null, hasCover = false, addedAt = 1, uploadedAt = 5))
    }

    // SYN-011: una posición recién guardada está pendiente de enviar
    @Test
    fun aSavedPositionIsPendingUntilMarkedSynced() = runBlocking {
        uploadedBook("A")
        repository.save("A", "a", 0.1)
        val dao = db.readingPositionDao()
        assertEquals(listOf("A"), dao.getPending().map { it.bookId })

        dao.markSynced("A", dao.get("A")!!.updatedAt)

        assertEquals(emptyList<String>(), dao.getPending().map { it.bookId })
    }

    // SYN-011: si se leyó más mientras se enviaba, la lectura nueva sigue pendiente
    @Test
    fun markingAnOlderReadingAsSyncedLeavesTheNewerOnePending() = runBlocking {
        uploadedBook("A")
        val dao = db.readingPositionDao()
        dao.upsert(com.pluk.reader.data.local.db.ReadingPositionEntity("A", "vieja", 100L, 0.1))
        dao.upsert(com.pluk.reader.data.local.db.ReadingPositionEntity("A", "nueva", 200L, 0.2))

        dao.markSynced("A", 100L)

        assertEquals(listOf("A"), dao.getPending().map { it.bookId })
    }

    // El servidor exige que el libro exista: la posición de un libro que aún no se subió (o que nunca se subirá)
    // no se envía, para no repetir rechazos
    @Test
    fun positionsOfBooksNotInTheCloudAreNotPending() = runBlocking {
        db.bookDao().insert(com.pluk.reader.data.local.db.BookEntity("sin-subir", "T", null, hasCover = false, addedAt = 1))
        uploadedBook("subido")
        repository.save("sin-subir", "a", 0.1)
        repository.save("subido", "b", 0.2)
        repository.save("sin-libro", "c", 0.3)

        assertEquals(listOf("subido"), db.readingPositionDao().getPending().map { it.bookId })
    }
}
