import Foundation
import Testing
@testable import ReaderDomain

// Mismos casos que `LibraryWatcherTest` de Android (SYN-015).
struct LibraryWatcherTests {
    actor FakeChanges: RemoteBookChanges {
        var since: [Int64] = []
        private var continuation: AsyncThrowingStream<[String], any Error>.Continuation?

        nonisolated func observeAlive(since: Int64) -> AsyncThrowingStream<[String], any Error> {
            AsyncThrowingStream { continuation in Task { await self.attach(continuation, since: since) } }
        }

        private func attach(_ continuation: AsyncThrowingStream<[String], any Error>.Continuation, since: Int64) {
            self.since.append(since)
            self.continuation = continuation
        }

        func emit(_ ids: [String]) { continuation?.yield(ids) }
        var isObserved: Bool { continuation != nil }
    }

    struct Known: KnownBooks {
        func knownBookIds() async -> Set<String> { ["ya"] }
    }

    actor Requests {
        var count = 0
        func add() { count += 1 }
    }

    struct Account: AccountRepository {
        let user: AccountUser?
        func currentUser() async -> AccountUser? { user }
        func signIn(email: String, password: String) async throws -> AccountUser { throw RemoteUnavailableError("no") }
    }

    let remote = FakeChanges()
    let requests = Requests()

    func watcher(signedIn: Bool = true) -> LibraryWatcher {
        let requests = requests
        return LibraryWatcher(
            remote: remote, known: Known(), account: Account(user: signedIn ? AccountUser(id: "u1", email: nil) : nil),
            now: { Date(timeIntervalSince1970: 100_000) }, onNewBooks: { await requests.add() }
        )
    }

    private func settle() async {
        for _ in 0..<200 { await Task.yield() }
    }

    @Test("SYN-015: un libro desconocido pide una pasada; uno conocido, no")
    func unknownBookRequestsAPass() async {
        let watcher = watcher()
        await watcher.startListening()
        while await !remote.isObserved { await Task.yield() }

        await remote.emit(["ya"])
        await settle()
        #expect(await requests.count == 0)

        await remote.emit(["ya", "nuevo"])
        await settle()
        #expect(await requests.count == 1)
        await watcher.stopListening()
    }

    @Test("SYN-015: solo lo cambiado en los últimos 15 minutos")
    func lookback() async {
        let watcher = watcher()
        await watcher.startListening()
        while await !remote.isObserved { await Task.yield() }
        #expect(await remote.since == [Int64(100_000_000 - 15 * 60_000)])
        await watcher.stopListening()
    }

    @Test("Sin sesión no escucha")
    func withoutSession() async {
        await watcher(signedIn: false).startListening()
        await settle()
        #expect(await remote.since.isEmpty)
    }
}
