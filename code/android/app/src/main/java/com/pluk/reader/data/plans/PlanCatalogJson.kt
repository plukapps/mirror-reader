package com.pluk.reader.data.plans

import com.pluk.reader.domain.plans.Plan
import com.pluk.reader.domain.plans.PlanId
import kotlin.math.floor
import org.json.JSONException
import org.json.JSONObject

/**
 * Lee el parámetro `plans` de Remote Config (ACC-007, ADR 0017) con la misma validación que el backend
 * (`functions/src/plans.ts`): si un plan es inválido, o faltan Free, Plus o Pro, el catálogo entero vale null y la app
 * sigue con los valores por defecto (ACC-008). Los planes que la app no conoce se ignoran.
 */
object PlanCatalogJson {
    /** Nombre del parámetro en Remote Config. */
    const val KEY = "plans"

    private const val GIB = 1024.0 * 1024 * 1024
    private const val MIB = 1024.0 * 1024
    /** Como `Number.isSafeInteger` del backend. */
    private const val MAX_SAFE = 9_007_199_254_740_991L
    private val PLAN_ID = Regex("^[a-z][a-z0-9_-]{0,31}$")
    private val CURRENCY = Regex("^[A-Z]{3}$")

    fun parse(json: String): List<Plan>? {
        val catalog = try {
            JSONObject(json)
        } catch (_: JSONException) {
            return null
        }
        val plans = mutableMapOf<String, Plan?>()
        for (key in catalog.keys()) {
            if (!PLAN_ID.matches(key)) return null
            plans[key] = parsePlan(key, catalog.opt(key)) ?: return null
        }
        return PlanId.entries.map { id -> plans[id.name.lowercase()] ?: return null }
    }

    private fun parsePlan(key: String, value: Any?): Plan? {
        val plan = value as? JSONObject ?: return null
        val storageGb = plan.number("storageGb") ?: return null
        val maxFileMb = plan.number("maxFileMb") ?: return null
        val monthly = plan.number("priceMonthly")?.takeIf { it >= 0 } ?: return null
        val yearly = plan.number("priceYearly")?.takeIf { it >= 0 } ?: return null
        val currency = (plan.opt("currency") as? String)?.takeIf(CURRENCY::matches) ?: return null
        val cloudBytes = bytes(storageGb * GIB) ?: return null
        val maxFileBytes = bytes(maxFileMb * MIB) ?: return null
        // Un id válido que la app no conoce se valida igual, pero no se devuelve.
        val id = PlanId.entries.firstOrNull { it.name.lowercase() == key } ?: PlanId.FREE
        return Plan(id, cloudBytes, maxFileBytes, monthly, yearly, currency)
    }

    /** El catálogo en el formato de Remote Config, para el valor por defecto de la app (ACC-008). */
    fun toJson(plans: List<Plan>): String = JSONObject().apply {
        plans.forEach { plan ->
            put(plan.id.name.lowercase(), JSONObject().apply {
                put("storageGb", plan.cloudBytes / GIB)
                put("maxFileMb", plan.maxFileBytes / MIB)
                put("priceMonthly", plan.monthlyPrice)
                put("priceYearly", plan.annualPrice)
                put("currency", plan.currency)
            })
        }
    }.toString()

    /** Un número del JSON (no un texto que parezca número), finito. */
    private fun JSONObject.number(name: String): Double? =
        (opt(name) as? Number)?.toDouble()?.takeIf { it.isFinite() }

    /** Unidades binarias redondeadas hacia abajo; tienen que quedar entre 1 y el entero seguro del backend. */
    private fun bytes(value: Double): Long? = floor(value).takeIf { it >= 1 && it <= MAX_SAFE }?.toLong()
}
