package com.pluk.reader.flags

import com.pluk.reader.domain.flags.Feature
import com.pluk.reader.domain.flags.Setting
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * ADR 0016: cada [Feature] tiene su parámetro en el template versionado de Remote Config, con el mismo valor por
 * defecto que la app. Si un deploy del template lo borrara, la app quedaría con su valor por defecto sin que nadie lo
 * note. Sin librería de JSON en los tests: se lee con una expresión regular, como `DefaultPlansTest`.
 */
class FeatureTemplateTest {
    // Los tests JVM corren desde `code/android/app`.
    private val template = File("../../backend/v1/remoteconfig.template.json").readText()

    private fun parameter(key: String): MatchResult? = Regex(
        """"${Regex.escape(key)}"\s*:\s*\{\s*"defaultValue"\s*:\s*\{\s*"value"\s*:\s*"([^"]*)"\s*\}[^}]*?"valueType"\s*:\s*"(\w+)"""",
    ).find(template)

    // ACC-011
    @Test
    fun everyFeatureIsInTheTemplateWithTheSameDefault() {
        for (feature in Feature.entries) {
            val param = parameter(feature.key)
            assertNotNull("falta ${feature.key} en remoteconfig.template.json", param)
            assertEquals(feature.key, feature.default.toString(), param!!.groupValues[1])
            assertEquals(feature.key, "BOOLEAN", param.groupValues[2])
        }
    }

    // GST-026: cada [Setting] está en el template como número, con el mismo valor por defecto
    @Test
    fun everySettingIsInTheTemplateWithTheSameDefault() {
        for (setting in Setting.entries) {
            val param = parameter(setting.key)
            assertNotNull("falta ${setting.key} en remoteconfig.template.json", param)
            assertEquals(setting.key, setting.default.toString(), param!!.groupValues[1])
            assertEquals(setting.key, "NUMBER", param.groupValues[2])
        }
    }
}
