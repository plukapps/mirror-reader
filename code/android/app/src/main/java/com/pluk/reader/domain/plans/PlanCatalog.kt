package com.pluk.reader.domain.plans

import com.pluk.reader.domain.account.StorageQuota

/** Planes de suscripción (PLN-001). Los textos (nombre, bajada, lo que incluye) están en la UI. */
enum class PlanId { FREE, PLUS, PRO }

/** Forma de pago elegida en Planes (PLN-004). */
enum class Billing { MONTHLY, ANNUAL }

/**
 * Un plan: espacio en la nube y precios. [cloudBytes] es null en Free, que usa la cuota de la cuenta (ACC-002).
 * Los precios son informativos (open-questions #1): la rebanada de compras los toma de la tienda.
 */
data class Plan(
    val id: PlanId,
    val cloudBytes: Long?,
    /** Tamaño máximo por archivo (PLN-014). Vale mientras no se conoce el del servidor (ACC-008, ACC-010). */
    val maxFileBytes: Long,
    val monthlyPrice: String,
    val annualPrice: String,
    /** Equivalencia por mes del precio anual ("$2.08"). */
    val annualPerMonth: String,
) {
    fun price(billing: Billing): String = if (billing == Billing.ANNUAL) annualPrice else monthlyPrice
}

/** Reglas de los planes sobre la cuenta. Los valores de cada plan, por ahora, son los de [DefaultPlans]. */
object PlanCatalog {
    const val ANNUAL_SAVING_PERCENT = 30

    fun of(id: PlanId): Plan = DefaultPlans.all.first { it.id == id }

    /** Id de cada plan en `users/{uid}.plan` y en el catálogo del backend (ACC-007, ADR 0017). */
    private val BACKEND_IDS = mapOf("free" to PlanId.FREE, "plus" to PlanId.PLUS, "pro" to PlanId.PRO)

    /** PLN-014, PLN-015: plan de la cuenta según la cuota; sin dato o desconocido, Free. */
    fun planOf(quota: StorageQuota?): PlanId = quota?.plan?.let(BACKEND_IDS::get) ?: PlanId.FREE

    /** PLN-014, ACC-008: tamaño máximo por archivo de la cuenta; el del servidor si se conoce, si no el fijo del plan. */
    fun maxFileBytes(quota: StorageQuota?): Long = quota?.maxFileBytes ?: of(planOf(quota)).maxFileBytes

    /**
     * PLN-001: espacio del plan. El de Free es la cuota de la cuenta si la cuenta está en Free; en una cuenta paga
     * (o sin dato), la de Free de hoy (K-291).
     */
    fun cloudBytes(plan: Plan, quota: StorageQuota?): Long =
        plan.cloudBytes ?: quota?.takeIf { it.isFreePlan }?.quotaBytes ?: DefaultPlans.FREE_QUOTA_BYTES
}

/** Botón de compra de P01 (PLN-006): "Elegir {plan} · {precio} por {año|mes}". Sin prueba gratis por ahora. */
data class PlanCta(val plan: Plan, val billing: Billing) {
    val price: String get() = plan.price(billing)
}

fun ctaFor(plan: PlanId, billing: Billing): PlanCta = PlanCta(PlanCatalog.of(plan), billing)
