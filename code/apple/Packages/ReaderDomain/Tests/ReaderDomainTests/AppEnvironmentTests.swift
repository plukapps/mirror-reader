import Testing
@testable import ReaderDomain

@Suite("Entorno de la app")
struct AppEnvironmentTests {
    @Test("APL-002: cada configuración tiene su entorno; un valor desconocido no cae en otro")
    func fromID() {
        #expect(AppEnvironment(id: "dev") == .dev)
        #expect(AppEnvironment(id: "stage") == .stage)
        #expect(AppEnvironment(id: "beta") == .beta)
        #expect(AppEnvironment(id: "prod") == .prod)
        #expect(AppEnvironment(id: "production") == nil)
        #expect(AppEnvironment(id: "") == nil)
    }

    @Test("APL-002: solo prod es producción")
    func production() {
        #expect(AppEnvironment.prod.isProduction)
        #expect(!AppEnvironment.dev.isProduction)
        #expect(!AppEnvironment.stage.isProduction)
        #expect(!AppEnvironment.beta.isProduction)
    }

    @Test("APL-001: la versión muestra el entorno fuera de producción")
    func versionLabel() {
        #expect(AppEnvironment.dev.versionLabel("0.1.0") == "0.1.0 · dev")
        #expect(AppEnvironment.stage.versionLabel("0.1.0") == "0.1.0 · stage")
        #expect(AppEnvironment.beta.versionLabel("0.1.0") == "0.1.0 · beta")
        #expect(AppEnvironment.prod.versionLabel("0.1.0") == "0.1.0")
    }
}
