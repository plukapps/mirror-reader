package com.pluk.reader.reader

import com.pluk.reader.data.epub.PublicationLoader
import com.pluk.reader.data.epub.LoadException
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class PublicationLoaderTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    private fun copyAsset(name: String): Uri {
        val file = File(context.cacheDir, name)
        instrumentation.context.assets.open(name).use { input ->
            file.outputStream().use { input.copyTo(it) }
        }
        return Uri.fromFile(file)
    }

    // LIB-002, RDR-007
    @Test
    fun opensValidEpub() = runBlocking {
        val result = PublicationLoader(context).load(copyAsset("minimal.epub"))
        assertTrue(result.isSuccess)
        assertEquals("Libro de prueba", result.getOrThrow().metadata.title)
    }

    // LIB-002: archivo que no es EPUB
    @Test
    fun failsWithClearMessageOnNonEpubFile() = runBlocking {
        val file = File(context.cacheDir, "basura.epub").apply { writeText("esto no es un epub") }
        val result = PublicationLoader(context).load(Uri.fromFile(file))
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is LoadException)
    }

    // Foco de revisión 5: archivo inaccesible
    @Test
    fun failsWithClearMessageOnMissingFile() = runBlocking {
        val missing = Uri.fromFile(File(context.cacheDir, "no-existe.epub"))
        val result = PublicationLoader(context).load(missing)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is LoadException)
    }
}
