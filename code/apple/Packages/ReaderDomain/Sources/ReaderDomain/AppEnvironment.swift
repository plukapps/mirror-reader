/// Entorno en el que corre la app (APL-001, APL-002, ADR 0021). Lo declara la configuración de build y la app lo
/// lee del `Info.plist` al arrancar; el dominio no lee el `Bundle`. Igual que `AppEnvironment` de Android (ADR 0020).
public enum AppEnvironment: String, Sendable, CaseIterable {
    case dev, stage, beta, prod

    /// Nil con un valor desconocido: una configuración mal armada no debe caer en otro entorno.
    public init?(id: String) {
        self.init(rawValue: id)
    }

    public var id: String { rawValue }

    public var isProduction: Bool { self == .prod }

    /// Versión para mostrar: fuera de producción lleva el entorno, así se sabe qué build está abierto.
    public func versionLabel(_ version: String) -> String {
        isProduction ? version : "\(version) · \(id)"
    }
}
