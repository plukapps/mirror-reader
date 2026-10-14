import Foundation
import ReaderDomain
import Testing

private let mib: Int64 = 1024 * 1024
private let gib: Int64 = 1024 * mib

/// El catálogo del template de Remote Config, por id de plan. El test corre desde el repo: el archivo está en
/// `code/backend/v1`, junto a `code/apple`.
private func templateCatalog() throws -> [String: [String: Any]] {
    let url = URL(fileURLWithPath: #filePath)
        .deletingLastPathComponent().deletingLastPathComponent().deletingLastPathComponent()
        .deletingLastPathComponent().deletingLastPathComponent().deletingLastPathComponent()
        .appendingPathComponent("backend/v1/remoteconfig.template.json")
    let template = try #require(try JSONSerialization.jsonObject(with: Data(contentsOf: url)) as? [String: Any])
    let parameters = try #require(template["parameters"] as? [String: Any])
    let plans = try #require(parameters["plans"] as? [String: Any])
    let defaultValue = try #require(plans["defaultValue"] as? [String: Any])
    let value = try #require(defaultValue["value"] as? String)
    return try #require(try JSONSerialization.jsonObject(with: Data(value.utf8)) as? [String: [String: Any]])
}

private func price(_ text: String) -> Double { Double(text.dropFirst()) ?? -1 }

@Test("ACC-008, ADR 0017: los valores por defecto de la app son los del template de Remote Config")
func defaultsAreTheTemplate() throws {
    let catalog = try templateCatalog()
    #expect(Set(catalog.keys) == ["free", "plus", "pro"])
    for plan in DefaultPlans.all {
        let expected = try #require(catalog[plan.id.rawValue])
        let storageGb = try #require(expected["storageGb"] as? Double)
        let maxFileMb = try #require(expected["maxFileMb"] as? Double)
        #expect(plan.storageBytes ?? DefaultPlans.freeQuotaBytes == Int64(storageGb * Double(gib)), "\(plan.id)")
        #expect(plan.maxFileBytes == Int64(maxFileMb * Double(mib)), "\(plan.id)")
        #expect(price(plan.monthlyPrice) == expected["priceMonthly"] as? Double, "\(plan.id)")
        #expect(price(plan.annualPrice) == expected["priceYearly"] as? Double, "\(plan.id)")
        #expect(expected["currency"] as? String == "USD")
    }
}
