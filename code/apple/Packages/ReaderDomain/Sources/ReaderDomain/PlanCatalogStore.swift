import Foundation

/// El catálogo de planes vigente y sus cambios (ACC-007, ACC-008, ADR 0017). La app usa `shared`, que publica
/// `FeatureFlags` con lo que baja de Remote Config; los tests usan uno propio.
public final class PlanCatalogStore: @unchecked Sendable {
    public static let shared = PlanCatalogStore()

    private let lock = NSLock()
    private var current = DefaultPlans.all
    private var continuations: [UUID: AsyncStream<[Plan]>.Continuation] = [:]

    public init() {}

    /// Siempre tiene Free, Plus y Pro, en ese orden.
    public var plans: [Plan] { lock.withLock { current } }

    /// nil (catálogo inválido o sin valor) vuelve a los por defecto.
    public func update(_ plans: [Plan]?) {
        let value = plans ?? DefaultPlans.all
        let subscribers = lock.withLock {
            current = value
            return Array(continuations.values)
        }
        for continuation in subscribers { continuation.yield(value) }
    }

    /// El catálogo actual y cada cambio.
    public func changes() -> AsyncStream<[Plan]> {
        let (stream, continuation) = AsyncStream<[Plan]>.makeStream(bufferingPolicy: .bufferingNewest(1))
        let id = UUID()
        let value = lock.withLock {
            continuations[id] = continuation
            return current
        }
        continuation.yield(value)
        continuation.onTermination = { [weak self] _ in
            guard let self else { return }
            self.lock.withLock { _ = self.continuations.removeValue(forKey: id) }
        }
        return stream
    }
}
