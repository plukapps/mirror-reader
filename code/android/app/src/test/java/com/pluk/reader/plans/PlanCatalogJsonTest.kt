package com.pluk.reader.plans

import com.pluk.reader.data.plans.PlanCatalogJson
import com.pluk.reader.domain.plans.DefaultPlans
import java.io.File
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** ACC-007, ACC-008, ADR 0017: el catálogo de Remote Config se lee y valida como en el backend (`plans.ts`). */
class PlanCatalogJsonTest {
    /** El valor de `plans` en el template del repo. Los tests JVM corren desde `code/android/app`. */
    private val template: String by lazy {
        val json = JSONObject(File("../../backend/v1/remoteconfig.template.json").readText())
        json.getJSONObject("parameters").getJSONObject("plans").getJSONObject("defaultValue").getString("value")
    }

    private fun valid(vararg overrides: Pair<String, String>): String {
        val plans = linkedMapOf(
            "free" to """{"storageGb":0.015,"maxFileMb":5,"priceMonthly":0,"priceYearly":0,"currency":"USD"}""",
            "plus" to """{"storageGb":2,"maxFileMb":20,"priceMonthly":2.99,"priceYearly":24.99,"currency":"USD"}""",
            "pro" to """{"storageGb":10,"maxFileMb":50,"priceMonthly":5.99,"priceYearly":49.99,"currency":"USD"}""",
        )
        overrides.forEach { (id, plan) -> plans[id] = plan }
        return plans.entries.joinToString(",", "{", "}") { (id, plan) -> "\"$id\":$plan" }
    }

    // ACC-008: los valores por defecto de la app son los del template; si alguien cambia uno y no el otro, falla
    @Test
    fun theTemplateIsTheDefaults() {
        assertEquals(DefaultPlans.all, PlanCatalogJson.parse(template))
    }

    // ACC-008: el valor por defecto que la app carga en Remote Config se lee como los valores por defecto
    @Test
    fun theAppDefaultJsonIsTheDefaults() {
        assertEquals(DefaultPlans.all, PlanCatalogJson.parse(PlanCatalogJson.toJson(DefaultPlans.all)))
    }

    // ACC-007: un catálogo válido, con unidades binarias redondeadas hacia abajo
    @Test
    fun readsAValidCatalog() {
        val plans = PlanCatalogJson.parse(valid())!!
        val plus = plans.first { it.id.name == "PLUS" }
        assertEquals(2L * 1024 * 1024 * 1024, plus.cloudBytes)
        assertEquals(20L * 1024 * 1024, plus.maxFileBytes)
        assertEquals(2.99, plus.monthlyPrice, 0.0)
        assertEquals(24.99, plus.annualPrice, 0.0)
        assertEquals((0.015 * 1024 * 1024 * 1024).toLong(), plans.first { it.id.name == "FREE" }.cloudBytes)
    }

    // ACC-007: un plan que la app no conoce se ignora
    @Test
    fun ignoresUnknownPlans() {
        val plans = PlanCatalogJson.parse(valid("family" to """{"storageGb":100,"maxFileMb":100,"priceMonthly":9,"priceYearly":90,"currency":"USD"}"""))
        assertEquals(listOf("FREE", "PLUS", "PRO"), plans!!.map { it.id.name })
    }

    // ACC-008: algo inválido invalida el catálogo entero (y quedan los por defecto)
    @Test
    fun rejectsTheWholeCatalogIfSomethingIsInvalid() {
        val invalid = listOf(
            "no es json", "[]", "null",
            valid().replace("\"free\"", "\"gratis\""), // falta free
            """{"free":{"storageGb":1,"maxFileMb":1,"priceMonthly":0,"priceYearly":0,"currency":"USD"}}""", // faltan plus y pro
            valid("pro" to """{"storageGb":-1,"maxFileMb":50,"priceMonthly":5.99,"priceYearly":49.99,"currency":"USD"}"""),
            valid("pro" to """{"storageGb":0,"maxFileMb":50,"priceMonthly":5.99,"priceYearly":49.99,"currency":"USD"}"""),
            valid("pro" to """{"storageGb":10,"maxFileMb":"50","priceMonthly":5.99,"priceYearly":49.99,"currency":"USD"}"""),
            valid("pro" to """{"storageGb":10,"maxFileMb":50,"priceMonthly":-1,"priceYearly":49.99,"currency":"USD"}"""),
            valid("pro" to """{"storageGb":10,"maxFileMb":50,"priceMonthly":5.99,"priceYearly":-1,"currency":"USD"}"""),
            valid("pro" to """{"storageGb":10,"maxFileMb":50,"priceMonthly":5.99,"priceYearly":49.99,"currency":""}"""),
            valid("pro" to """{"storageGb":10,"maxFileMb":50,"priceMonthly":5.99,"currency":"USD"}"""),
            valid("pro" to """{"storageGb":1e12,"maxFileMb":50,"priceMonthly":5.99,"priceYearly":49.99,"currency":"USD"}"""),
            valid("pro" to """{"storageGb":10,"maxFileMb":1e-9,"priceMonthly":5.99,"priceYearly":49.99,"currency":"USD"}"""),
            valid("Pro Plus" to """{"storageGb":10,"maxFileMb":50,"priceMonthly":5.99,"priceYearly":49.99,"currency":"USD"}"""),
            valid("pro" to "null"),
        )
        for (json in invalid) assertNull(json, PlanCatalogJson.parse(json))
    }
}
