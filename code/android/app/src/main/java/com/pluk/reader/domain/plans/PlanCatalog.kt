package com.pluk.reader.domain.plans

import com.pluk.reader.domain.account.StorageQuota
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Planes de suscripción (PLN-001). Los textos (nombre, bajada, lo que incluye) están en la UI. */
enum class PlanId { FREE, PLUS, PRO }

/** Forma de pago elegida en Planes (PLN-004). */
enum class Billing { MONTHLY, ANNUAL }

/**
 * Un plan del catálogo (ACC-007): espacio, máximo por archivo y precios. En Free, la app muestra la cuota de la cuenta
 * si la cuenta está en Free (PLN-001, [PlanCatalog.cloudBytes]). Los precios son informativos (open-questions #1): la
 * rebanada de compras los toma de la tienda.
 */
data class Plan(
    val id: PlanId,
    val cloudBytes: Long,
    /** Tamaño máximo por archivo (PLN-014). Vale mientras no se conoce el del servidor (ACC-008, ACC-010). */
    val maxFileBytes: Long,
    val monthlyPrice: Double,
    val annualPrice: Double,
    val currency: String,
) {
    /** PLN-001: "$2.99" según la forma de pago. */
    fun price(billing: Billing): String = formatPrice(if (billing == Billing.ANNUAL) annualPrice else monthlyPrice)

    /** Equivalencia por mes del precio anual: el anual dividido 12, redondeado al centavo ("$2.08"). */
    val annualPerMonth: String
        get() = formatPrice(BigDecimal.valueOf(annualPrice).divide(BigDecimal(12), 2, RoundingMode.HALF_UP).toDouble())
}

/**
 * PLN-001: precio fijo con "$" y dos decimales ("$2.99"); sin costo, "$0". No usa el formato del país del dispositivo
 * (decisión del usuario, 2026-10-14): el precio real llega de la tienda con el cobro.
 */
fun formatPrice(value: Double): String = if (value == 0.0) "$0" else "$" + String.format(Locale.US, "%.2f", value)

/**
 * Reglas de los planes sobre la cuenta, con el catálogo vigente: el de Remote Config si es válido, si no
 * [DefaultPlans] (ACC-007, ACC-008, ADR 0017).
 */
object PlanCatalog {
    const val ANNUAL_SAVING_PERCENT = 30

    private val current = MutableStateFlow(DefaultPlans.all)

    /** El catálogo vigente y sus cambios. Siempre tiene Free, Plus y Pro, en ese orden. */
    val plans: StateFlow<List<Plan>> = current.asStateFlow()

    /** Lo publica la configuración remota (K-303); null (catálogo inválido o sin valor) vuelve a los por defecto. */
    fun update(plans: List<Plan>?) {
        current.value = plans ?: DefaultPlans.all
    }

    fun of(id: PlanId): Plan = current.value.first { it.id == id }

    /** Id de cada plan en `users/{uid}.plan` y en el catálogo del backend (ACC-007, ADR 0017). */
    private val BACKEND_IDS = mapOf("free" to PlanId.FREE, "plus" to PlanId.PLUS, "pro" to PlanId.PRO)

    /** PLN-014, PLN-015: plan de la cuenta según la cuota; sin dato o desconocido, Free. */
    fun planOf(quota: StorageQuota?): PlanId = quota?.plan?.let(BACKEND_IDS::get) ?: PlanId.FREE

    /** PLN-014, ACC-008: tamaño máximo por archivo de la cuenta; el del servidor si se conoce, si no el fijo del plan. */
    fun maxFileBytes(quota: StorageQuota?): Long = quota?.maxFileBytes ?: of(planOf(quota)).maxFileBytes

    /** PLN-001: espacio del plan. El de Free es la cuota de la cuenta si la cuenta está en Free; si no, el del catálogo (K-291). */
    fun cloudBytes(plan: Plan, quota: StorageQuota?): Long =
        if (plan.id == PlanId.FREE && quota?.isFreePlan == true) quota.quotaBytes else plan.cloudBytes
}

/** Botón de compra de P01 (PLN-006): "Elegir {plan} · {precio} por {año|mes}". Sin prueba gratis por ahora. */
data class PlanCta(val plan: Plan, val billing: Billing) {
    val price: String get() = plan.price(billing)
}

fun ctaFor(plan: PlanId, billing: Billing): PlanCta = PlanCta(PlanCatalog.of(plan), billing)
