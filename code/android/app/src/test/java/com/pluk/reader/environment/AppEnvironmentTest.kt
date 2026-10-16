package com.pluk.reader.environment

import com.pluk.reader.domain.environment.AppEnvironment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** AND-008, ADR 0020: el entorno sale del nombre que declara el flavor. */
class AppEnvironmentTest {
    @Test
    fun `AND-008 cada flavor tiene su entorno`() {
        assertEquals(AppEnvironment.DEV, AppEnvironment.fromName("dev"))
        assertEquals(AppEnvironment.STAGE, AppEnvironment.fromName("stage"))
        assertEquals(AppEnvironment.PROD, AppEnvironment.fromName("prod"))
    }

    @Test
    fun `AND-008 un nombre desconocido falla en vez de caer en un entorno`() {
        assertThrows(IllegalArgumentException::class.java) { AppEnvironment.fromName("production") }
    }

    @Test
    fun `AND-008 solo prod es produccion`() {
        assertTrue(AppEnvironment.PROD.isProduction)
        assertFalse(AppEnvironment.DEV.isProduction)
        assertFalse(AppEnvironment.STAGE.isProduction)
    }

    @Test
    fun `AND-007 la version muestra el entorno fuera de produccion`() {
        assertEquals("1.0 · dev", AppEnvironment.DEV.versionLabel("1.0"))
        assertEquals("1.0 · stage", AppEnvironment.STAGE.versionLabel("1.0"))
        assertEquals("1.0", AppEnvironment.PROD.versionLabel("1.0"))
    }
}
