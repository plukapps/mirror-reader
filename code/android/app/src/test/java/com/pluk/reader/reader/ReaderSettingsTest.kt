package com.pluk.reader.reader

import com.pluk.reader.domain.model.LineSpacing
import com.pluk.reader.domain.model.ReaderFont
import com.pluk.reader.domain.model.ReaderSettings
import com.pluk.reader.domain.model.ReadingTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderSettingsTest {
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

    // RDR-009: la animación de página viene activada y se puede desactivar
    @Test
    fun pageAnimationIsOnByDefaultAndToggles() {
        assertEquals(true, ReaderSettings().pageAnimation)
        assertEquals(false, ReaderSettings().togglePageAnimation().pageAnimation)
        assertEquals(true, ReaderSettings().togglePageAnimation().togglePageAnimation().pageAnimation)
    }

    // RDR-014: tipo de letra (3 opciones) con serifa por defecto, como el diseño
    @Test
    fun fontDefaultsToSerifAndChangesOnlyTheFont() {
        assertEquals(ReaderFont.SERIF, ReaderSettings().font)
        val base = ReaderSettings(theme = ReadingTheme.DARK, fontScale = 1.3, pageAnimation = false)
        assertEquals(base.copy(font = ReaderFont.MONO), base.withFont(ReaderFont.MONO))
        assertEquals(ReaderFont.SANS, ReaderSettings().withFont(ReaderFont.SANS).font)
    }

    // RDR-014: interlineado (2 opciones), normal por defecto
    @Test
    fun lineSpacingDefaultsToNormalAndChangesOnlyTheSpacing() {
        assertEquals(LineSpacing.NORMAL, ReaderSettings().lineSpacing)
        val base = ReaderSettings(theme = ReadingTheme.SEPIA, font = ReaderFont.SANS)
        assertEquals(base.copy(lineSpacing = LineSpacing.WIDE), base.withLineSpacing(LineSpacing.WIDE))
    }

    // RDR-014: el tema se elige directamente (Clásico, Sepia, Noche), no solo con "siguiente"
    @Test
    fun themeCanBePickedDirectly() {
        assertEquals(ReadingTheme.SEPIA, ReaderSettings().withTheme(ReadingTheme.SEPIA).theme)
        assertEquals(ReadingTheme.LIGHT, ReaderSettings(theme = ReadingTheme.DARK).withTheme(ReadingTheme.LIGHT).theme)
        val base = ReaderSettings(font = ReaderFont.MONO, fontScale = 1.2)
        assertEquals(base.copy(theme = ReadingTheme.DARK), base.withTheme(ReadingTheme.DARK))
    }

    // ADR 0009: cada opción de tipo de letra apunta a una familia distinta con nombre propio
    @Test
    fun eachFontHasItsOwnFamilyName() {
        assertEquals("Newsreader", ReaderFont.SERIF.familyName)
        assertEquals("Host Grotesk", ReaderFont.SANS.familyName)
        assertEquals("JetBrains Mono", ReaderFont.MONO.familyName)
        assertEquals(ReaderFont.entries.size, ReaderFont.entries.map { it.familyName }.toSet().size)
    }

    // RDR-014: amplio separa más las líneas que normal
    @Test
    fun wideSpacingIsLooserThanNormal() {
        assertEquals(1.4, LineSpacing.NORMAL.lineHeight, 0.0001)
        assertEquals(1.7, LineSpacing.WIDE.lineHeight, 0.0001)
        assertTrue(LineSpacing.WIDE.lineHeight > LineSpacing.NORMAL.lineHeight)
    }
}
