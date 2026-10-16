package com.pluk.reader.environment

import com.pluk.reader.BuildConfig
import com.pluk.reader.domain.environment.AppEnvironment
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * AND-007, AND-008, ADR 0020: el build de cada flavor declara un entorno válido y un paquete propio.
 * Corre en la variante con la que se lancen los tests (`testDevDebugUnitTest`, `testStageDebugUnitTest`; AGP no arma tests JVM de release, beta y prod se revisan en el APK).
 */
class BuildEnvironmentTest {
    @Test
    fun `AND-008 el flavor declara un entorno conocido`() {
        AppEnvironment.fromName(BuildConfig.ENVIRONMENT)
    }

    @Test
    fun `AND-007 el paquete lleva el sufijo del entorno salvo en produccion`() {
        val expected = when (AppEnvironment.fromName(BuildConfig.ENVIRONMENT)) {
            AppEnvironment.DEV -> "com.pluk.reader.dev"
            AppEnvironment.STAGE -> "com.pluk.reader.stage"
            AppEnvironment.BETA -> "com.pluk.reader.beta"
            AppEnvironment.PROD -> "com.pluk.reader"
        }
        assertEquals(expected, BuildConfig.APPLICATION_ID)
    }

    @Test
    fun `AND-007 dev y stage son de depuracion, beta y prod de release`() {
        val debug = when (AppEnvironment.fromName(BuildConfig.ENVIRONMENT)) {
            AppEnvironment.DEV, AppEnvironment.STAGE -> true
            AppEnvironment.BETA, AppEnvironment.PROD -> false
        }
        assertEquals(debug, BuildConfig.DEBUG)
    }
}
