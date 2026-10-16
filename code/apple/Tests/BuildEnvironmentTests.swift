import Foundation
import Testing
import ReaderDomain
@testable import Reader

/// APL-001, APL-002, ADR 0021: los tests corren en la configuración Dev (esquema Reader).
@Suite("Entorno del build")
struct BuildEnvironmentTests {
    @Test("APL-002: la configuración Dev declara el entorno dev")
    func environmentIsDev() {
        #expect(BuildEnvironment.current() == .dev)
    }

    @Test("APL-001: el bundle ID lleva el sufijo del entorno")
    func bundleIdentifier() {
        #expect(Bundle.main.bundleIdentifier?.hasPrefix("com.pluk.reader.dev") == true)
    }

    @Test("APL-001: dev es un build de depuración")
    func debugBuild() {
        #if DEBUG
        #expect(Bool(true))
        #else
        Issue.record("La configuración Dev debe compilar como debug")
        #endif
    }

    @Test("APL-001: la versión muestra el entorno")
    func versionLabel() {
        #expect(BuildEnvironment.versionLabel().hasSuffix(" · dev"))
    }
}
