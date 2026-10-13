package com.pluk.reader.library

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pluk.reader.data.epub.PublicationLoader
import com.pluk.reader.data.library.EpubImporter
import com.pluk.reader.data.library.LibraryFiles
import com.pluk.reader.data.local.db.ReaderDatabase
import com.pluk.reader.data.repository.CloudBooksRepositoryImpl
import com.pluk.reader.domain.model.ImportOutcome
import com.pluk.reader.domain.remote.RemoteBook
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Orden de la biblioteca con libros locales y de la nube (LIB-021), sobre Room. */
@RunWith(AndroidJUnit4::class)
class CloudBooksOrderTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context: Context = instrumentation.targetContext
    private lateinit var db: ReaderDatabase
    private lateinit var importer: EpubImporter
    private lateinit var cloud: CloudBooksRepositoryImpl

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, ReaderDatabase::class.java).build()
        val files = LibraryFiles(context)
        importer = EpubImporter(context, files, PublicationLoader(context), db.bookDao())
        cloud = CloudBooksRepositoryImpl(db.bookDao(), files, importer)
    }

    @After
    fun tearDown() {
        db.close()
        File(context.filesDir, "books").deleteRecursively()
        File(context.filesDir, "covers").deleteRecursively()
    }

    private suspend fun importMinimal(): String {
        val file = File(context.cacheDir, "minimal.epub")
        instrumentation.context.assets.open("minimal.epub").use { input -> file.outputStream().use { input.copyTo(it) } }
        return (importer.import(Uri.fromFile(file)) as ImportOutcome.Imported).bookId
    }

    private fun remote(id: String, createdAt: Long?) = RemoteBook(id, "T$id", emptyList(), 1, createdAt)

    private suspend fun order() = db.bookDao().observeAll().first().map { it.id }

    // LIB-021: los libros de la nube se ordenan por cuándo entraron a la cuenta, intercalados con los locales
    @Test
    fun cloudBooksAreOrderedByTheirCloudDate() = runBlocking {
        val local = importMinimal()
        val importedAt = db.bookDao().get(local)!!.addedAt

        cloud.addCloudOnly(listOf(remote("viejo", importedAt - 10_000), remote("nuevo", importedAt + 10_000)))

        assertEquals(listOf("nuevo", local, "viejo"), order())
    }

    // LIB-021: un libro ya guardado toma la fecha de la nube en la próxima pasada
    @Test
    fun aKnownBookTakesTheCloudDate() = runBlocking {
        val local = importMinimal()

        cloud.addCloudOnly(listOf(remote(local, 1_000)))

        assertEquals(1_000L, db.bookDao().get(local)!!.addedAt)
    }
}
