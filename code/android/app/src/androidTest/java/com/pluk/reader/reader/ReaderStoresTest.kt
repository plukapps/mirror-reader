package com.pluk.reader.reader

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.util.Url
import org.readium.r2.shared.util.mediatype.MediaType

@RunWith(AndroidJUnit4::class)
class ReaderStoresTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun clean() {
        context.getSharedPreferences("reader_locators", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("reader_settings", Context.MODE_PRIVATE).edit().clear().commit()
    }

    private fun locator(href: String, progression: Double? = null) = Locator(
        href = Url(href)!!,
        mediaType = MediaType.XHTML,
        locations = Locator.Locations(totalProgression = progression),
    )

    // RDR-006
    @Test
    fun locatorRoundTrips() {
        val store = PrefsLocatorStore(context)
        store.save("libro-1", locator("OEBPS/ch2.xhtml", 0.75))
        val loaded = store.load("libro-1")
        assertEquals(Url("OEBPS/ch2.xhtml"), loaded?.href)
        assertEquals(0.75, loaded?.locations?.totalProgression ?: -1.0, 0.0001)
    }

    // RDR-006: libro desconocido
    @Test
    fun unknownBookHasNoLocator() {
        assertNull(PrefsLocatorStore(context).load("no-existe"))
    }

    // RDR-006: cada libro guarda la suya
    @Test
    fun locatorsAreIndependentPerBook() {
        val store = PrefsLocatorStore(context)
        store.save("A", locator("a.xhtml"))
        store.save("B", locator("b.xhtml"))
        assertEquals(Url("a.xhtml"), store.load("A")?.href)
        assertEquals(Url("b.xhtml"), store.load("B")?.href)
    }

    // RDR-006: dato guardado dañado no debe romper la app
    @Test
    fun corruptedLocatorIsIgnored() {
        context.getSharedPreferences("reader_locators", Context.MODE_PRIVATE)
            .edit().putString("roto", "{no es json").commit()
        assertNull(PrefsLocatorStore(context).load("roto"))
    }

    // RDR-002, RDR-003
    @Test
    fun settingsRoundTrip() {
        val store = PrefsSettingsStore(context)
        val settings = ReaderSettings(scroll = true, theme = ReaderTheme.SEPIA, fontScale = 1.4)
        store.save(settings)
        assertEquals(settings, store.load())
    }

    // Primera ejecución: valores por defecto
    @Test
    fun settingsDefaultWhenNothingSaved() {
        assertEquals(ReaderSettings(), PrefsSettingsStore(context).load())
    }
}
