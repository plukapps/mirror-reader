import Foundation

/// Valores por defecto de los planes (ACC-008, ADR 0017): los mismos del template de Remote Config
/// (`code/backend/v1/remoteconfig.template.json`, lo comprueba `DefaultPlansTests`). Valen mientras la app no conoce los
/// del servidor: el máximo por archivo llega en `users/{uid}.maxFileBytes`; los precios y el espacio, cuando la app lea
/// el catálogo (K-290). `storageBytes` es nil en Free, que usa la cuota de la cuenta (ACC-002). Los textos de cada
/// plan los pone la app (`PlanTexts.swift`, K-295).
public enum DefaultPlans {
    private static let mib: Int64 = 1024 * 1024
    private static let gib: Int64 = 1024 * mib

    /// Espacio de Free, para cuando no se conoce la cuota de la cuenta o la cuenta es paga (K-291).
    public static let freeQuotaBytes: Int64 = 15 * mib

    public static let free = Plan(
        id: .free, storageBytes: nil, maxFileBytes: 2 * mib,
        monthlyPrice: "$0", annualPrice: "$0", annualPerMonth: nil
    )
    public static let plus = Plan(
        id: .plus, storageBytes: 5 * gib, maxFileBytes: 50 * mib,
        monthlyPrice: "$2.99", annualPrice: "$24.99", annualPerMonth: "$2.08"
    )
    public static let pro = Plan(
        id: .pro, storageBytes: 50 * gib, maxFileBytes: 100 * mib,
        monthlyPrice: "$5.99", annualPrice: "$49.99", annualPerMonth: "$4.17"
    )

    public static let all = [free, plus, pro]
}
