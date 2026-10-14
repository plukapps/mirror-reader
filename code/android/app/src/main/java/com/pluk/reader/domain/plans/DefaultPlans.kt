package com.pluk.reader.domain.plans

/**
 * Valores por defecto de los planes (ACC-008, ADR 0017): los mismos del template de Remote Config
 * (`code/backend/v1/remoteconfig.template.json`, lo comprueba `DefaultPlansTest`). Valen mientras la app no conoce
 * los del servidor: el máximo por archivo llega en `users/{uid}.maxFileBytes`; los precios y el espacio, cuando la app
 * lea el catálogo (K-290). [Plan.cloudBytes] es null en Free, que usa la cuota de la cuenta (ACC-002).
 */
object DefaultPlans {
    private const val MIB = 1024L * 1024
    private const val GIB = 1024L * MIB

    /** Espacio de Free, para cuando no se conoce la cuota de la cuenta o la cuenta es paga (K-291). */
    const val FREE_QUOTA_BYTES = 15 * MIB

    val free = Plan(PlanId.FREE, null, 2 * MIB, "$0", "$0", "$0")
    val plus = Plan(PlanId.PLUS, 5 * GIB, 50 * MIB, "$2.99", "$24.99", "$2.08")
    val pro = Plan(PlanId.PRO, 50 * GIB, 100 * MIB, "$5.99", "$49.99", "$4.17")

    val all: List<Plan> = listOf(free, plus, pro)
}
