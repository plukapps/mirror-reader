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
import com.pluk.reader.domain.model.ImportOutcome
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EpubImporterTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context: Context = instrumentation.targetContext
    private lateinit var db: ReaderDatabase
    private lateinit var files: LibraryFiles
    private lateinit var importer: EpubImporter

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, ReaderDatabase::class.java).build()
        files = LibraryFiles(context)
        importer = EpubImporter(context, files, PublicationLoader(context), db.bookDao())
    }

    @After
    fun tearDown() {
        db.close()
        File(context.filesDir, "books").deleteRecursively()
        File(context.filesDir, "covers").deleteRecursively()
    }

    private fun asset(name: String, target: String = name): Uri {
        val file = File(context.cacheDir, target)
        instrumentation.context.assets.open(name).use { input -> file.outputStream().use { input.copyTo(it) } }
        return Uri.fromFile(file)
    }

    // LIB-003, LIB-004
    @Test
    fun importsEpubWithMetadataAndStoresACopy() = runBlocking {
        val outcome = importer.import(asset("minimal.epub")) as ImportOutcome.Imported
        assertEquals("Libro de prueba", outcome.title)
        val book = db.bookDao().get(outcome.bookId)!!
        assertEquals("Autor de prueba", book.author)
        assertTrue(files.bookFile(outcome.bookId).exists())
    }

    // LIB-003: el mismo archivo, aunque se llame distinto, es un solo libro
    @Test
    fun importingSameContentTwiceDoesNotDuplicate() = runBlocking {
        val first = importer.import(asset("minimal.epub")) as ImportOutcome.Imported
        val second = importer.import(asset("minimal.epub", "copia-renombrada.epub"))
        assertEquals(ImportOutcome.AlreadyInLibrary(first.bookId, first.title), second)
        assertEquals(1, db.bookDao().observeAll().first().size)
    }

    // LIB-002
    @Test
    fun rejectsFileThatIsNotAnEpubAndLeavesNothingBehind() = runBlocking {
        val garbage = File(context.cacheDir, "basura.epub").apply { writeText("esto no es un epub") }
        val outcome = importer.import(Uri.fromFile(garbage))
        assertTrue(outcome is ImportOutcome.Rejected)
        assertTrue(db.bookDao().observeAll().first().isEmpty())
        assertTrue(File(context.filesDir, "books").listFiles().orEmpty().isEmpty())
    }

    // LIB-002: archivo inaccesible
    @Test
    fun rejectsMissingFile() = runBlocking {
        val outcome = importer.import(Uri.fromFile(File(context.cacheDir, "no-existe.epub")))
        assertTrue(outcome is ImportOutcome.Rejected)
    }

    // LIB-004: el fixture no trae portada
    @Test
    fun bookWithoutCoverIsMarkedAsSuch() = runBlocking {
        val outcome = importer.import(asset("minimal.epub")) as ImportOutcome.Imported
        assertFalse(db.bookDao().get(outcome.bookId)!!.hasCover)
    }

    // LIB-007: un libro solo en la nube se completa al importar su archivo, sin perder los metadatos de la nube
    @Test
    fun importingTheFileOfACloudOnlyBookCompletesIt() = runBlocking {
        val first = importer.import(asset("minimal.epub")) as ImportOutcome.Imported
        // Se simula que el libro estaba solo en la nube: sin archivo local.
        db.bookDao().markUploaded(first.bookId, 77, 1)
        db.openHelper.writableDatabase.execSQL("UPDATE books SET isDownloaded = 0, title = 'Titulo de la nube' WHERE id = '${first.bookId}'")
        files.bookFile(first.bookId).delete()

        val again = importer.import(asset("minimal.epub", "minimal-2.epub"))
        assertTrue(again is ImportOutcome.Imported)
        val book = db.bookDao().get(first.bookId)!!
        assertTrue(book.isDownloaded)
        assertEquals("Titulo de la nube", book.title)
        assertEquals(77L, book.uploadedAt)
        assertTrue(files.bookFile(first.bookId).exists())
    }
}
