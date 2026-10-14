package com.pluk.reader.plans

import com.pluk.reader.domain.plans.DefaultPlans
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * ACC-008, ADR 0017: los valores por defecto de la app son los del template de Remote Config. Si alguien cambia uno y
 * no el otro, este test falla. Sin librería de JSON en los tests: el template tiene una forma fija y se lee con
 * expresiones regulares.
 */
class DefaultPlansTest {
    private val catalog: Map<String, Map<String, String>> by lazy {
        // Los tests JVM corren desde `code/android/app`.
        val template = File("../../backend/v1/remoteconfig.template.json").readText()
        val value = Regex(""""value"\s*:\s*"((?:\\.|[^"\\])*)"""").find(template)!!.groupValues[1].replace("\\\"", "\"")
        Regex(""""(\w+)":\{([^}]*)\}""").findAll(value).associate { plan ->
            plan.groupValues[1] to Regex(""""(\w+)":"?([^",}]+)"?""").findAll(plan.groupValues[2])
                .associate { it.groupValues[1] to it.groupValues[2] }
        }
    }

    @Test
    fun theDefaultsAreTheTemplate() {
        assertEquals(setOf("free", "plus", "pro"), catalog.keys)
        for (plan in DefaultPlans.all) {
            val id = plan.id.name.lowercase()
            val expected = catalog.getValue(id)
            val storage = plan.cloudBytes ?: DefaultPlans.FREE_QUOTA_BYTES
            assertEquals(id, bytes(expected.getValue("storageGb"), GIB), storage)
            assertEquals(id, bytes(expected.getValue("maxFileMb"), MIB), plan.maxFileBytes)
            assertEquals(id, expected.getValue("priceMonthly").toDouble(), price(plan.monthlyPrice), 0.0)
            assertEquals(id, expected.getValue("priceYearly").toDouble(), price(plan.annualPrice), 0.0)
            assertEquals(id, "USD", expected.getValue("currency"))
        }
    }

    private fun bytes(value: String, unit: Long): Long = (value.toDouble() * unit).toLong()

    private fun price(text: String): Double = text.removePrefix("$").toDouble()

    private companion object {
        const val MIB = 1024L * 1024
        const val GIB = 1024L * MIB
    }
}
