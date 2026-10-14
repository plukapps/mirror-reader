import Foundation
import Testing
@testable import ReaderDomain

@Suite("Interruptores de funciones")
struct FeatureFlagsTests {
    /// Lo que tiene el servidor se aplica al bajar o al llegar un cambio en vivo.
    actor FakeSource: RemoteFlagSource {
        var server: [String: Bool] = [:]
        private var applied: [String: Bool] = [:]
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
        }
        func value(_ key: String) async -> Bool? { applied[key] }
        nonisolated func updates() -> AsyncStream<Void> {
            let (stream, continuation) = AsyncStream<Void>.makeStream()
            Task { await self.keep(continuation) }
            return stream
        }
        private func keep(_ continuation: AsyncStream<Void>.Continuation) { self.continuation = continuation }
        /// El servidor publicó un cambio.
        func push() {
            applied.merge(server) { $1 }
            continuation?.yield()
        }
        var isListening: Bool { continuation != nil }
    }

    private func settle() async {
        for _ in 0..<50 { await Task.yield() }
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
