package com.pluk.reader.domain.plans

/**
 * Valores por defecto de los planes (ACC-008, ADR 0017): los mismos del template de Remote Config
 * (`code/backend/v1/remoteconfig.template.json`, lo comprueba `PlanCatalogJsonTest`). Valen mientras la app no conoce
 * los del servidor: el máximo por archivo llega en `users/{uid}.maxFileBytes`; los precios y el espacio, cuando la app
 * lea el catálogo (K-290). Free usa la cuota de la cuenta si la cuenta está en Free (ACC-002).
 */
object DefaultPlans {
    private const val MIB = 1024L * 1024
    private const val GIB = 1024L * MIB

    val free = Plan(PlanId.FREE, 15 * MIB, 2 * MIB, 0.0, 0.0, "USD")
    val plus = Plan(PlanId.PLUS, 5 * GIB, 50 * MIB, 2.99, 24.99, "USD")
    val pro = Plan(PlanId.PRO, 50 * GIB, 100 * MIB, 5.99, 49.99, "USD")

    val all: List<Plan> = listOf(free, plus, pro)
}
