package com.pluk.reader.reader

import com.pluk.reader.domain.model.ReaderSettings
import com.pluk.reader.domain.model.ReadingTheme
import com.pluk.reader.ui.reader.toEpubPreferences
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.readium.r2.navigator.preferences.Theme
import org.readium.r2.shared.ExperimentalReadiumApi

// Corre en el emulador: EpubPreferences de Readium usa clases de Android que no existen en la JVM.
@OptIn(ExperimentalReadiumApi::class)
@RunWith(AndroidJUnit4::class)
class ReaderSettingsMappingTest {
    // RDR-001
    @Test
    fun scrollMapsToPreferences() {
        assertEquals(true, ReaderSettings(scroll = true).toEpubPreferences().scroll)
        assertEquals(false, ReaderSettings(scroll = false).toEpubPreferences().scroll)
    }

    // RDR-003
    @Test
    fun themeMapsToReadiumTheme() {
        assertEquals(Theme.DARK, ReaderSettings(theme = ReadingTheme.DARK).toEpubPreferences().theme)
        assertEquals(Theme.SEPIA, ReaderSettings(theme = ReadingTheme.SEPIA).toEpubPreferences().theme)
        assertEquals(Theme.LIGHT, ReaderSettings(theme = ReadingTheme.LIGHT).toEpubPreferences().theme)
    }

    // RDR-002
    @Test
    fun fontScaleMapsToFontSize() {
        assertEquals(1.2, ReaderSettings(fontScale = 1.2).toEpubPreferences().fontSize ?: 0.0, 0.0001)
    }

    // RDR-002: el margen de página de Readium se anula; el único margen es el del contenedor.
    @Test
    fun pageMarginsAreZero() {
        assertEquals(0.0, ReaderSettings().toEpubPreferences().pageMargins ?: -1.0, 0.0)
    }
}
