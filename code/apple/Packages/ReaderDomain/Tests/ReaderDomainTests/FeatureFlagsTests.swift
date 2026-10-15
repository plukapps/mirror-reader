import Foundation
import Testing
@testable import ReaderDomain

@Suite("Interruptores de funciones")
struct FeatureFlagsTests {
    /// Lo que tiene el servidor se aplica al bajar o al llegar un cambio en vivo.
    actor FakeSource: RemoteFlagSource {
        var server: [String: Bool] = [:]
        private var applied: [String: Bool] = [:]
        /// El catálogo de planes del servidor, ya leído (nil: inválido); se aplica al bajar o con un cambio en vivo.
        var serverPlans: [Plan]? = DefaultPlans.all
        private var appliedPlans: [Plan]? = DefaultPlans.all
        func setServerPlans(_ plans: [Plan]?) { serverPlans = plans }
        private(set) var plan: String?
        var offline = false
        private var continuation: AsyncStream<Void>.Continuation?

        func setServer(_ key: String, _ value: Bool) { server[key] = value }
        func setOffline(_ value: Bool) { offline = value }
        func setPlan(_ plan: String) async throws { self.plan = plan }
        func activate() async throws {}
        func fetchAndActivate() async throws {
            if offline { throw RemoteUnavailableError("sin red") }
            applied.merge(server) { $1 }
            appliedPlans = serverPlans
        }
        func value(_ key: String) async -> Bool? { applied[key] }
        func plans() async -> [Plan]? { appliedPlans }
        nonisolated func updates() -> AsyncStream<Void> {
            let (stream, continuation) = AsyncStream<Void>.makeStream()
            Task { await self.keep(continuation) }
            return stream
        }
        private func keep(_ continuation: AsyncStream<Void>.Continuation) { self.continuation = continuation }
        /// El servidor publicó un cambio.
        func push() {
            applied.merge(server) { $1 }
            appliedPlans = serverPlans
            continuation?.yield()
        }
        var isListening: Bool { continuation != nil }
    }

    private func settle() async {
        for _ in 0..<50 { await Task.yield() }
    }

    /// Espera (con un tope) a que el store tenga `plans`: la publicación cruza varios actores.
    private func waitFor(_ plans: [Plan], in store: PlanCatalogStore) async {
        for _ in 0..<2_000 where store.plans != plans { await Task.yield() }
    }

    private let cheaperPlus = [
        DefaultPlans.free,
        Plan(id: .plus, storageBytes: DefaultPlans.plus.storageBytes, maxFileBytes: DefaultPlans.plus.maxFileBytes,
             monthlyPrice: 1.99, annualPrice: 19.99, currency: "USD"),
        DefaultPlans.pro,
    ]

    @Test("ACC-007, K-290: al bajar la configuración, el catálogo del servidor pasa a ser el vigente")
    func publishesPlanCatalog() async {
        let source = FakeSource()
        await source.setServerPlans(cheaperPlus)
        let store = PlanCatalogStore()
        let flags = FeatureFlags(source: source, catalog: store)
        await flags.startListening()
        await waitFor(cheaperPlus, in: store)
        #expect(store.plans == cheaperPlus)
    }

    @Test("ACC-007, ACC-008: un cambio llega en vivo; si el catálogo pasa a ser inválido, vuelven los por defecto")
    func catalogLiveChangesAndInvalid() async {
        let source = FakeSource()
        await source.setServerPlans(cheaperPlus)
        let store = PlanCatalogStore()
        let flags = FeatureFlags(source: source, catalog: store)
        await flags.startListening()
        await waitFor(cheaperPlus, in: store)
        #expect(store.plans == cheaperPlus)
        await source.setServerPlans(nil)
        await source.push()
        await waitFor(DefaultPlans.all, in: store)
        #expect(store.plans == DefaultPlans.all)
    }

    @Test("SYN-017: si no se sabe todavía, vale prendida; sin conexión también")
    func onByDefault() async {
        let source = FakeSource()
        await source.setOffline(true)
        let flags = FeatureFlags(source: source)
        #expect(await flags.isEnabled(.notesSync))
        await flags.startListening()
        await settle()
        #expect(await flags.isEnabled(.notesSync))
        #expect(await FeatureFlags(source: nil).isEnabled(.notesSync))
    }

    @Test("SYN-017, ACC-011: apagada en el servidor, se apaga al bajar la configuración")
    func followsTheServer() async {
        let source = FakeSource()
        await source.setServer("notes_sync_enabled", false)
        let flags = FeatureFlags(source: source)
        await flags.startListening()
        await settle()
        #expect(await !flags.isEnabled(.notesSync))
    }

    @Test("SYN-017: con la app a la vista, prender o apagar llega en vivo")
    func appliesLiveChanges() async {
        let source = FakeSource()
        let flags = FeatureFlags(source: source)
        let changes = await flags.changes(.notesSync)
        var iterator = changes.makeAsyncIterator()
        #expect(await iterator.next() == true)
        await flags.startListening()
        await settle()
        await source.setServer("notes_sync_enabled", false)
        await source.push()
        #expect(await iterator.next() == false)
        await source.setServer("notes_sync_enabled", true)
        await source.push()
        #expect(await iterator.next() == true)
        await flags.stopListening()
    }

    @Test("ACC-011: el plan de la cuenta viaja como señal")
    func sendsThePlan() async {
        let source = FakeSource()
        await FeatureFlags(source: source, plan: { "plus" }).startListening()
        await settle()
        #expect(await source.plan == "plus")
    }
}
