package com.pluk.reader.reader

import com.pluk.reader.domain.model.ReaderSettings
import com.pluk.reader.domain.model.ReadingTheme
import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderSettingsTest {
    // RDR-001
    @Test
    fun toggleScrollSwitchesMode() {
        val settings = ReaderSettings().toggleScroll()
        assertEquals(true, settings.scroll)
        assertEquals(false, settings.toggleScroll().scroll)
    }

    // RDR-003
    @Test
    fun themeCyclesLightDarkSepiaLight() {
        var s = ReaderSettings()
        assertEquals(ReadingTheme.LIGHT, s.theme)
        s = s.nextTheme(); assertEquals(ReadingTheme.DARK, s.theme)
        s = s.nextTheme(); assertEquals(ReadingTheme.SEPIA, s.theme)
        s = s.nextTheme(); assertEquals(ReadingTheme.LIGHT, s.theme)
    }

    // RDR-002
    @Test
    fun fontScaleStepsByTenth() {
        assertEquals(1.1, ReaderSettings().biggerFont().fontScale, 0.0001)
        assertEquals(0.9, ReaderSettings().smallerFont().fontScale, 0.0001)
    }

    // RDR-002: límites
    @Test
    fun fontScaleIsClamped() {
        assertEquals(2.5, ReaderSettings(fontScale = 2.5).biggerFont().fontScale, 0.0001)
        assertEquals(0.5, ReaderSettings(fontScale = 0.5).smallerFont().fontScale, 0.0001)
    }
}
