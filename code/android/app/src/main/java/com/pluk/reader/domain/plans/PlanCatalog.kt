package com.pluk.reader.domain.plans

import com.pluk.reader.domain.account.StorageQuota

/** Planes de suscripción (PLN-001). Los textos (nombre, bajada, lo que incluye) están en la UI. */
enum class PlanId { FREE, READER, LIBRARY }

/** Forma de pago elegida en Planes (PLN-004). */
enum class Billing { MONTHLY, ANNUAL }

/**
 * Un plan: espacio en la nube y precios. [cloudBytes] es null en Gratis, que usa la cuota de la cuenta (ACC-002).
 * Los precios son de ejemplo y fijos (open-questions #1): la rebanada de compras los toma de la tienda.
 */
data class Plan(
    val id: PlanId,
    val cloudBytes: Long?,
    /** Tamaño máximo por archivo (PLN-014). Por ahora lo controla solo la app. */
    val maxFileBytes: Long,
    val monthlyPrice: String,
    val annualPrice: String,
    /** Equivalencia por mes del precio anual ("$2.08"). */
    val annualPerMonth: String,
) {
    fun price(billing: Billing): String = if (billing == Billing.ANNUAL) annualPrice else monthlyPrice
}

object PlanCatalog {
    private const val MIB = 1024L * 1024
    private const val GIB = 1024L * MIB

    /** Cuota de Gratis de hoy (backend `FREE_PLAN`), para cuando nunca se leyó la de la cuenta. */
    const val FREE_QUOTA_BYTES = 15 * MIB
    const val ANNUAL_SAVING_PERCENT = 30

    val free = Plan(PlanId.FREE, null, 2 * MIB, "$0", "$0", "$0")
    val reader = Plan(PlanId.READER, 5 * GIB, 50 * MIB, "$2.99", "$24.99", "$2.08")
    val library = Plan(PlanId.LIBRARY, 50 * GIB, 100 * MIB, "$5.99", "$49.99", "$4.17")

    val all: List<Plan> = listOf(free, reader, library)

    fun of(id: PlanId): Plan = all.first { it.id == id }

    /** PLN-001: espacio del plan; el de Gratis es la cuota de la cuenta. */
    fun cloudBytes(plan: Plan, quota: StorageQuota?): Long = plan.cloudBytes ?: quota?.quotaBytes ?: FREE_QUOTA_BYTES
}

/** Botón de compra de P01 (PLN-006): "Elegir {plan} · {precio} por {año|mes}". Sin prueba gratis por ahora. */
data class PlanCta(val plan: Plan, val billing: Billing) {
    val price: String get() = plan.price(billing)
}

fun ctaFor(plan: PlanId, billing: Billing): PlanCta = PlanCta(PlanCatalog.of(plan), billing)
