import Foundation
import Testing
@testable import ReaderDomain

private let mib: Int64 = 1024 * 1024
private let gib: Int64 = 1024 * mib

/// El valor de `plans` en el template del repo. El test corre desde el repo: el archivo está en `code/backend/v1`,
/// junto a `code/apple`.
private func templatePlans() throws -> String {
    let url = URL(fileURLWithPath: #filePath)
        .deletingLastPathComponent().deletingLastPathComponent().deletingLastPathComponent()
        .deletingLastPathComponent().deletingLastPathComponent().deletingLastPathComponent()
        .appendingPathComponent("backend/v1/remoteconfig.template.json")
    let template = try #require(try JSONSerialization.jsonObject(with: Data(contentsOf: url)) as? [String: Any])
    let parameters = try #require(template["parameters"] as? [String: Any])
    let plans = try #require(parameters["plans"] as? [String: Any])
    let defaultValue = try #require(plans["defaultValue"] as? [String: Any])
    return try #require(defaultValue["value"] as? String)
}

private func valid(_ overrides: [String: String] = [:]) -> String {
    var plans = [
        "free": #"{"storageGb":0.015,"maxFileMb":5,"priceMonthly":0,"priceYearly":0,"currency":"USD"}"#,
        "plus": #"{"storageGb":2,"maxFileMb":20,"priceMonthly":2.99,"priceYearly":24.99,"currency":"USD"}"#,
        "pro": #"{"storageGb":10,"maxFileMb":50,"priceMonthly":5.99,"priceYearly":49.99,"currency":"USD"}"#,
    ]
    plans.merge(overrides) { $1 }
    return "{" + plans.map { "\"\($0.key)\":\($0.value)" }.joined(separator: ",") + "}"
}

@Suite("Catálogo de planes desde Remote Config")
struct PlanCatalogJSONTests {
    @Test("ACC-008, ADR 0017: los valores por defecto de la app son los del template; si se separan, falla")
    func templateIsTheDefaults() throws {
        #expect(PlanCatalogJSON.parse(try templatePlans()) == DefaultPlans.all)
    }

    @Test("ACC-008: el JSON por defecto que la app carga en Remote Config se lee como los valores por defecto")
    func appDefaultJSONIsTheDefaults() {
        #expect(PlanCatalogJSON.parse(PlanCatalogJSON.json(DefaultPlans.all)) == DefaultPlans.all)
    }

    @Test("ACC-007: un catálogo válido, con unidades binarias redondeadas hacia abajo")
    func readsValidCatalog() throws {
        let plans = try #require(PlanCatalogJSON.parse(valid()))
        #expect(plans.map(\.id) == [.free, .plus, .pro])
        #expect(plans[1].storageBytes == 2 * gib)
        #expect(plans[1].maxFileBytes == 20 * mib)
        #expect(plans[1].monthlyPrice == 2.99)
        #expect(plans[1].annualPrice == 24.99)
        #expect(plans[0].storageBytes == Int64((0.015 * Double(gib)).rounded(.down)))
    }

    @Test("ACC-007: un plan que la app no conoce se ignora")
    func ignoresUnknownPlans() {
        let json = valid(["family": #"{"storageGb":100,"maxFileMb":100,"priceMonthly":9,"priceYearly":90,"currency":"USD"}"#])
        #expect(PlanCatalogJSON.parse(json)?.map(\.id) == [.free, .plus, .pro])
    }

    @Test("ACC-008: algo inválido invalida el catálogo entero (y quedan los por defecto)")
    func rejectsInvalid() {
        let invalid = [
            "no es json", "[]", "null",
            valid().replacingOccurrences(of: "\"free\"", with: "\"gratis\""),
            #"{"free":{"storageGb":1,"maxFileMb":1,"priceMonthly":0,"priceYearly":0,"currency":"USD"}}"#,
            valid(["pro": #"{"storageGb":-1,"maxFileMb":50,"priceMonthly":5.99,"priceYearly":49.99,"currency":"USD"}"#]),
            valid(["pro": #"{"storageGb":0,"maxFileMb":50,"priceMonthly":5.99,"priceYearly":49.99,"currency":"USD"}"#]),
            valid(["pro": #"{"storageGb":10,"maxFileMb":"50","priceMonthly":5.99,"priceYearly":49.99,"currency":"USD"}"#]),
            valid(["pro": #"{"storageGb":10,"maxFileMb":50,"priceMonthly":-1,"priceYearly":49.99,"currency":"USD"}"#]),
            valid(["pro": #"{"storageGb":10,"maxFileMb":50,"priceMonthly":5.99,"priceYearly":-1,"currency":"USD"}"#]),
            valid(["pro": #"{"storageGb":10,"maxFileMb":50,"priceMonthly":5.99,"priceYearly":49.99,"currency":""}"#]),
            valid(["pro": #"{"storageGb":10,"maxFileMb":50,"priceMonthly":5.99,"currency":"USD"}"#]),
            valid(["pro": #"{"storageGb":1e12,"maxFileMb":50,"priceMonthly":5.99,"priceYearly":49.99,"currency":"USD"}"#]),
            valid(["pro": #"{"storageGb":10,"maxFileMb":1e-9,"priceMonthly":5.99,"priceYearly":49.99,"currency":"USD"}"#]),
            valid(["Pro Plus": #"{"storageGb":10,"maxFileMb":50,"priceMonthly":5.99,"priceYearly":49.99,"currency":"USD"}"#]),
            valid(["pro": "null"]),
            valid(["pro": #"{"storageGb":true,"maxFileMb":50,"priceMonthly":5.99,"priceYearly":49.99,"currency":"USD"}"#]),
        ]
        for json in invalid { #expect(PlanCatalogJSON.parse(json) == nil, "\(json)") }
    }
}
