import Foundation
import Testing
@testable import ReaderDomain

/// ADR 0016: cada `Feature` tiene su parámetro en el template versionado de Remote Config, con el mismo valor por
/// defecto que la app. Publicar el template reemplaza el del proyecto entero: si faltara, el deploy lo borraría.
@Suite("Template de Remote Config")
struct FeatureTemplateTests {
    private func parameters() throws -> [String: [String: Any]] {
        // Este archivo está en code/apple/Packages/ReaderDomain/Tests/ReaderDomainTests.
        let url = URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent().deletingLastPathComponent().deletingLastPathComponent()
            .deletingLastPathComponent().deletingLastPathComponent().deletingLastPathComponent()
            .appendingPathComponent("backend/v1/remoteconfig.template.json")
        let json = try JSONSerialization.jsonObject(with: Data(contentsOf: url)) as? [String: Any]
        return try #require(json?["parameters"] as? [String: [String: Any]])
    }

    @Test("ACC-011: cada interruptor está en el template, booleano y con el mismo valor por defecto")
    func everyFeatureIsInTheTemplate() throws {
        let parameters = try parameters()
        for feature in Feature.allCases {
            let param = try #require(parameters[feature.key], "falta \(feature.key) en remoteconfig.template.json")
            #expect(param["valueType"] as? String == "BOOLEAN")
            let value = (param["defaultValue"] as? [String: Any])?["value"] as? String
            #expect(value == String(feature.defaultValue))
        }
    }
}
