package com.pluk.reader.library

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pluk.reader.data.epub.PublicationLoader
import com.pluk.reader.data.library.EpubImporter
import com.pluk.reader.data.library.LibraryFiles
import com.pluk.reader.data.local.db.ReaderDatabase
import com.pluk.reader.data.repository.BookEditorImpl
import com.pluk.reader.data.repository.CloudBooksRepositoryImpl
import com.pluk.reader.data.repository.PositionRepositoryImpl
import com.pluk.reader.domain.model.ImportOutcome
import com.pluk.reader.domain.remote.RemoteBook
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BookEditorTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context: Context = instrumentation.targetContext
    private lateinit var db: ReaderDatabase
    private lateinit var files: LibraryFiles
    private lateinit var importer: EpubImporter
    private lateinit var editor: BookEditorImpl
    private lateinit var cloud: CloudBooksRepositoryImpl
    private lateinit var positions: PositionRepositoryImpl

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, ReaderDatabase::class.java).build()
        files = LibraryFiles(context)
        importer = EpubImporter(context, files, PublicationLoader(context), db.bookDao())
        editor = BookEditorImpl(context, db, files)
        cloud = CloudBooksRepositoryImpl(db.bookDao(), files, importer)
        positions = PositionRepositoryImpl(db.readingPositionDao())
    }

    @After
    fun tearDown() {
        db.close()
        File(context.filesDir, "books").deleteRecursively()
        File(context.filesDir, "covers").deleteRecursively()
    }

    private fun asset(name: String): Uri {
        val file = File(context.cacheDir, name)
        instrumentation.context.assets.open(name).use { input -> file.outputStream().use { input.copyTo(it) } }
        return Uri.fromFile(file)
    }

    private suspend fun importMinimal(): String = (importer.import(asset("minimal.epub")) as ImportOutcome.Imported).bookId

    private fun remote(id: String) = RemoteBook(id = id, title = "Drácula", authors = listOf("Bram Stoker"), sizeBytes = 1)

    // LIB-020: quitar borra el libro, su posición y sus archivos de este dispositivo
    @Test
    fun removeDeletesBookPositionAndFiles() = runBlocking {
        val id = importMinimal()
        positions.save(id, """{"href":"c1.xhtml"}""", 0.4)

        editor.remove(id)

        assertNull(db.bookDao().get(id))
        assertNull(positions.get(id))
        assertFalse(files.bookFile(id).exists())
        assertFalse(files.coverFile(id).exists())
        assertEquals(listOf(id), db.bookDao().getRemovedIds())
    }

    // LIB-020: la sincronización no vuelve a traer un libro quitado
    @Test
    fun cloudDoesNotBringBackARemovedBook() = runBlocking {
        cloud.addCloudOnly(listOf(remote("quitado"), remote("otro")))
        editor.remove("quitado")

        cloud.addCloudOnly(listOf(remote("quitado"), remote("otro")))

        assertNull(db.bookDao().get("quitado"))
        assertEquals(listOf("otro"), cloud.cloudOnlyBookIds())
    }

    // LIB-020: importar el mismo archivo lo devuelve a la biblioteca
    @Test
    fun importingAgainRestoresARemovedBook() = runBlocking {
        val id = importMinimal()
        editor.remove(id)

        val again = importer.import(asset("minimal.epub"))

        assertTrue(again is ImportOutcome.Imported)
        assertTrue(db.bookDao().get(id) != null)
        assertTrue(db.bookDao().getRemovedIds().isEmpty())
    }

    // LIB-019: la foto elegida reemplaza la portada y cambia su versión
    @Test
    fun replaceCoverWritesScaledJpegAndBumpsVersion() = runBlocking {
        val id = importMinimal()
        val photo = File(context.cacheDir, "foto.png")
        Bitmap.createBitmap(800, 1200, Bitmap.Config.ARGB_8888).also { bitmap ->
            photo.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }

        assertTrue(editor.replaceCover(id, Uri.fromFile(photo).toString()))

        val book = db.bookDao().get(id)!!
        assertTrue(book.hasCover)
        assertNotEquals(0L, book.coverVersion)
        val saved = android.graphics.BitmapFactory.decodeFile(files.coverFile(id).absolutePath)
        assertEquals(600, saved.height)
    }

    // LIB-019: una imagen que no se puede leer no cambia nada
    @Test
    fun replaceCoverWithUnreadableImageFails() = runBlocking {
        val id = importMinimal()
        val notAnImage = File(context.cacheDir, "nada.png").apply { writeText("no es una imagen") }

        assertFalse(editor.replaceCover(id, Uri.fromFile(notAnImage).toString()))
        assertEquals(0L, db.bookDao().get(id)!!.coverVersion)
    }
}
