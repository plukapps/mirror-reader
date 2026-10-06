package com.pluk.reader.reader

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class TocLoadTest {
    // RDR-004
    @Test
    fun readsChaptersFromRealEpub() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val file = File(context.cacheDir, "minimal.epub")
        instrumentation.context.assets.open("minimal.epub").use { input ->
            file.outputStream().use { input.copyTo(it) }
        }
        val publication = PublicationLoader(context).load(Uri.fromFile(file)).getOrThrow()

        val entries = flattenToc(publication.tableOfContents)

        assertEquals(listOf("Capítulo 1", "Capítulo 2"), entries.map { it.title })
        assertEquals(listOf(0, 0), entries.map { it.depth })
    }
}
