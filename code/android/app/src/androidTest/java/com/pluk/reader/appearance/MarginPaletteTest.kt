package com.pluk.reader.appearance

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pluk.reader.ui.theme.DarkMarginPalette
import com.pluk.reader.ui.theme.LightPalette
import com.pluk.reader.ui.theme.LocalMarginPalette
import com.pluk.reader.ui.theme.MarginColors
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** La paleta provista por la raíz y la clara forzada del onboarding y el lector (PRF-021, PRF-022). */
@RunWith(AndroidJUnit4::class)
class MarginPaletteTest {
    @get:Rule
    val compose = createComposeRule()

    // PRF-022: en oscuro, los tokens toman la paleta oscura y lo que va sobre amarillo no cambia.
    @Test
    fun darkPaletteIsProvided() {
        var paper = Color.Unspecified
        var ink = Color.Unspecified
        var inkFill = Color.Unspecified
        compose.setContent {
            CompositionLocalProvider(LocalMarginPalette provides DarkMarginPalette) {
                paper = MarginColors.Paper
                ink = MarginColors.Ink
                inkFill = MarginColors.InkFill
            }
        }
        compose.waitForIdle()
        assertEquals(Color(0xFF130000), paper)
        assertEquals(Color(0xFFFDFDFD), ink)
        assertEquals(Color(0xFF2B2421), inkFill)
        assertEquals(Color(0xFF130000), MarginColors.OnYellow)
    }

    // PRF-021: el onboarding y el lector se dibujan en claro aunque la app esté en oscuro.
    @Test
    fun lightPaletteOverridesDark() {
        var paper = Color.Unspecified
        compose.setContent {
            CompositionLocalProvider(LocalMarginPalette provides DarkMarginPalette) {
                LightPalette { paper = MarginColors.Paper }
            }
        }
        compose.waitForIdle()
        assertEquals(Color(0xFFF1F1F1), paper)
    }
}
