package com.pluk.reader.appearance

import com.pluk.reader.domain.appearance.Appearance
import com.pluk.reader.domain.appearance.DEFAULT_APPEARANCE
import com.pluk.reader.domain.appearance.isDarkMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Modo efectivo de la apariencia (PRF-021). */
class AppearanceTest {
    // PRF-021: Claro y Oscuro no dependen del dispositivo.
    @Test
    fun lightAndDarkIgnoreTheSystem() {
        assertFalse(isDarkMode(Appearance.LIGHT, systemDark = true))
        assertFalse(isDarkMode(Appearance.LIGHT, systemDark = false))
        assertTrue(isDarkMode(Appearance.DARK, systemDark = false))
        assertTrue(isDarkMode(Appearance.DARK, systemDark = true))
    }

    // PRF-021: Sistema sigue el modo del dispositivo.
    @Test
    fun systemFollowsTheDevice() {
        assertTrue(isDarkMode(Appearance.SYSTEM, systemDark = true))
        assertFalse(isDarkMode(Appearance.SYSTEM, systemDark = false))
    }

    // PRF-021: por defecto, Sistema.
    @Test
    fun defaultIsSystem() {
        assertEquals(Appearance.SYSTEM, DEFAULT_APPEARANCE)
    }
}
