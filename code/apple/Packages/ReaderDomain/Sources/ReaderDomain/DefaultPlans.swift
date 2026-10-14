import Foundation

/// Valores por defecto de los planes (ACC-008, ADR 0017): los mismos del template de Remote Config
/// (`code/backend/v1/remoteconfig.template.json`, lo comprueba `DefaultPlansTests`). Valen mientras la app no conoce los
/// del servidor: el máximo por archivo llega en `users/{uid}.maxFileBytes`; los precios y el espacio, cuando la app lea
/// el catálogo (K-290). `storageBytes` es nil en Gratis, que usa la cuota de la cuenta (ACC-002).
public enum DefaultPlans {
    private static let mib: Int64 = 1024 * 1024
    private static let gib: Int64 = 1024 * mib

    /// Espacio de Gratis, para cuando no se conoce la cuota de la cuenta o la cuenta es paga (K-291).
    public static let freeQuotaBytes: Int64 = 15 * mib

    public static let free = Plan(
        id: .free, name: "Gratis", tagline: "Para leer de vez en cuando", storageBytes: nil, maxFileBytes: 2 * mib,
        includes: ["Lectura y notas sin tope", "Sincroniza en todos tus dispositivos", "Archivos de hasta 2 MB"],
        monthlyPrice: "$0", annualPrice: "$0", annualPerMonth: nil
    )
    public static let plus = Plan(
        id: .plus, name: "Lector", tagline: "Para quien lee siempre", storageBytes: 5 * gib, maxFileBytes: 50 * mib,
        includes: ["Temas y fuentes", "Exportá notas a Markdown, Obsidian y Notion", "Archivos de hasta 50 MB"],
        monthlyPrice: "$2.99", annualPrice: "$24.99", annualPerMonth: "$2.08"
    )
    public static let pro = Plan(
        id: .pro, name: "Biblioteca", tagline: "Para quien tiene una biblioteca", storageBytes: 50 * gib,
        maxFileBytes: 100 * mib,
        includes: ["Todo lo de Lector", "Archivos de hasta 100 MB"],
        monthlyPrice: "$5.99", annualPrice: "$49.99", annualPerMonth: "$4.17"
    )

    public static let all = [free, plus, pro]
}
