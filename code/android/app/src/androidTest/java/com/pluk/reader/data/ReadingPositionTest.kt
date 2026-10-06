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
        repository.save("libro-1", """{"href":"ch2.xhtml"}""")
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
        repository.save("A", "a")
        repository.save("B", "b")
        assertEquals("a", repository.get("A"))
        assertEquals("b", repository.get("B"))
    }

    // RDR-006: guardar de nuevo reemplaza, no duplica
    @Test
    fun savingAgainReplacesThePosition() = runBlocking {
        repository.save("A", "primera")
        repository.save("A", "segunda")
        assertEquals("segunda", repository.get("A"))
    }
}
