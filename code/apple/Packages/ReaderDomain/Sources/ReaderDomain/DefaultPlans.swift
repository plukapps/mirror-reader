import Foundation

/// Valores por defecto de los planes (ACC-008, ADR 0017): los mismos del template de Remote Config
/// (`code/backend/v1/remoteconfig.template.json`, lo comprueba `PlanCatalogJSONTests`). Valen mientras la app no conoce los
/// del servidor: el máximo por archivo llega en `users/{uid}.maxFileBytes`; los precios y el espacio, cuando la app lea
/// el catálogo (K-290). Free usa la cuota de la cuenta si la cuenta está en Free (ACC-002). Los textos de cada
/// plan los pone la app (`PlanTexts.swift`, K-295).
public enum DefaultPlans {
    private static let mib: Int64 = 1024 * 1024
    private static let gib: Int64 = 1024 * mib

    public static let free = Plan(id: .free, storageBytes: 15 * mib, maxFileBytes: 2 * mib, monthlyPrice: 0, annualPrice: 0, currency: "USD")
    public static let plus = Plan(id: .plus, storageBytes: 5 * gib, maxFileBytes: 50 * mib, monthlyPrice: 2.99, annualPrice: 24.99, currency: "USD")
    public static let pro = Plan(id: .pro, storageBytes: 50 * gib, maxFileBytes: 100 * mib, monthlyPrice: 5.99, annualPrice: 49.99, currency: "USD")

    public static let all = [free, plus, pro]
}
