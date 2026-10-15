import Foundation

/// Lee el parámetro `plans` de Remote Config (ACC-007, ADR 0017) con la misma validación que el backend
/// (`functions/src/plans.ts`) y que Android (`PlanCatalogJson`): si un plan es inválido, o faltan Free, Plus o Pro, el
/// catálogo entero vale nil y la app sigue con los valores por defecto (ACC-008). Los planes que la app no conoce se
/// ignoran.
public enum PlanCatalogJSON {
    /// Nombre del parámetro en Remote Config.
    public static let key = "plans"

    private static let gib = 1024.0 * 1024 * 1024
    private static let mib = 1024.0 * 1024
    /// Como `Number.isSafeInteger` del backend.
    private static let maxSafe = 9_007_199_254_740_991.0

    public static func parse(_ json: String) -> [Plan]? {
        guard let catalog = (try? JSONSerialization.jsonObject(with: Data(json.utf8))) as? [String: Any] else { return nil }
        var plans: [String: Plan] = [:]
        for (key, value) in catalog {
            guard key.range(of: "^[a-z][a-z0-9_-]{0,31}$", options: .regularExpression) != nil,
                  let plan = plan(key, value)
            else { return nil }
            plans[key] = plan
        }
        var result: [Plan] = []
        for id in PlanId.allCases {
            guard let plan = plans[id.rawValue] else { return nil }
            result.append(plan)
        }
        return result
    }

    /// El catálogo en el formato de Remote Config, para el valor por defecto de la app (ACC-008).
    public static func json(_ plans: [Plan]) -> String {
        let catalog = Dictionary(uniqueKeysWithValues: plans.map { plan in
            (plan.id.rawValue, [
                "storageGb": Double(plan.storageBytes) / gib,
                "maxFileMb": Double(plan.maxFileBytes) / mib,
                "priceMonthly": plan.monthlyPrice,
                "priceYearly": plan.annualPrice,
                "currency": plan.currency,
            ] as [String: Any])
        })
        let data = (try? JSONSerialization.data(withJSONObject: catalog, options: [.sortedKeys])) ?? Data()
        return String(decoding: data, as: UTF8.self)
    }

    private static func plan(_ key: String, _ value: Any) -> Plan? {
        guard let plan = value as? [String: Any],
              let storageGb = number(plan["storageGb"]),
              let maxFileMb = number(plan["maxFileMb"]),
              let monthly = number(plan["priceMonthly"]), monthly >= 0,
              let yearly = number(plan["priceYearly"]), yearly >= 0,
              let currency = plan["currency"] as? String,
              currency.range(of: "^[A-Z]{3}$", options: .regularExpression) != nil,
              let storage = bytes(storageGb * gib),
              let maxFile = bytes(maxFileMb * mib)
        else { return nil }
        // Un id válido que la app no conoce se valida igual, pero no se devuelve.
        return Plan(id: PlanId(rawValue: key) ?? .free, storageBytes: storage, maxFileBytes: maxFile,
                    monthlyPrice: monthly, annualPrice: yearly, currency: currency)
    }

    /// Un número del JSON (no un texto ni un booleano), finito.
    private static func number(_ value: Any?) -> Double? {
        guard let number = value as? NSNumber, CFGetTypeID(number) != CFBooleanGetTypeID() else { return nil }
        let double = number.doubleValue
        return double.isFinite ? double : nil
    }

    /// Unidades binarias redondeadas hacia abajo; tienen que quedar entre 1 y el entero seguro del backend.
    private static func bytes(_ value: Double) -> Int64? {
        let floored = value.rounded(.down)
        return floored >= 1 && floored <= maxSafe ? Int64(floored) : nil
    }
}
