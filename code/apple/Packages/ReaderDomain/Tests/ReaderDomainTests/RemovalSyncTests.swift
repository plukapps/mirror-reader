import Foundation
import Testing
@testable import ReaderDomain

// Mismos casos que `RemovalSyncTest` de Android (LIB-020, LIB-008).
struct RemovalSyncTests {
    actor FakeRemote: RemoteRemovals {
        var calls: [String] = []
        var offline = false
        var failFilesFor: Set<String> = []
        var deleted: [String] = []
        private var continuation: AsyncThrowingStream<[String], any Error>.Continuation?

        func markDeleted(bookId: String) async throws {
            if offline { throw RemoteUnavailableError("sin red") }
            calls.append("marcar:\(bookId)")
        }

        func deleteFiles(bookId: String) async throws {
            if offline || failFilesFor.contains(bookId) { throw RemoteUnavailableError("sin red") }
            calls.append("archivos:\(bookId)")
        }

        func deletedBookIds() async throws -> [String] {
            if offline { throw RemoteUnavailableError("sin red") }
            return deleted
        }

        nonisolated func observeDeleted() -> AsyncThrowingStream<[String], any Error> {
            AsyncThrowingStream { continuation in Task { await self.attach(continuation) } }
        }

        private func attach(_ continuation: AsyncThrowingStream<[String], any Error>.Continuation) { self.continuation = continuation }
        func emit(_ ids: [String]) { continuation?.yield(ids) }
        var isObserved: Bool { continuation != nil }
        func set(offline: Bool) { self.offline = offline }
        func set(deleted: [String]) { self.deleted = deleted }
        func failFiles(_ id: String) { failFilesFor.insert(id) }
    }

    actor FakeLocal: BookRemovals {
        var pending: [String] = []
        var removedByCloud: [String] = []
        func pendingCloudRemovals() async -> [String] { pending }
        func markCloudRemoved(bookId: String) async { pending.removeAll { $0 == bookId } }
        func removeDeletedInCloud(bookIds: [String]) async { removedByCloud += bookIds }
        func add(_ id: String) { pending.append(id) }
    }

    struct Account: AccountRepository {
        let user: AccountUser?
        func currentUser() async -> AccountUser? { user }
        func signIn(email: String, password: String) async throws -> AccountUser { throw RemoteUnavailableError("no") }
    }

    let remote = FakeRemote()
    let local = FakeLocal()

    func removals(signedIn: Bool = true) -> RemovalSync {
        RemovalSync(remote: remote, local: local, account: Account(user: signedIn ? AccountUser(id: "u1", email: nil) : nil))
    }

    @Test("LIB-020: primero se marca en la nube, después se borran los archivos y recién ahí queda hecho")
    func pushOrder() async throws {
        await local.add("a")
        await local.add("b")
        try await removals().sync()
        #expect(await remote.calls == ["marcar:a", "archivos:a", "marcar:b", "archivos:b"])
        #expect(await local.pending.isEmpty)
    }

    @Test("LIB-020: sin conexión queda pendiente y la pasada lo informa")
    func offlineKeepsPending() async {
        await local.add("a")
        await remote.set(offline: true)
        await #expect(throws: RemoteUnavailableError.self) { try await removals().sync() }
        #expect(await local.pending == ["a"])
    }

    @Test("LIB-020: si fallan los archivos, se reintenta todo la próxima vez")
    func failedFilesStayPending() async {
        await local.add("a")
        await remote.failFiles("a")
        try? await removals().sync()
        #expect(await local.pending == ["a"])
    }

    @Test("LIB-008: al sincronizar se quitan aquí los libros borrados en otro dispositivo")
    func pullRemovesDeletedElsewhere() async throws {
        await remote.set(deleted: ["x", "y"])
        try await removals().sync()
        #expect(await local.removedByCloud == ["x", "y"])
    }

    @Test("LIB-020: un libro abierto en el lector se quita al cerrarlo")
    func openBookWaitsForClose() async throws {
        await remote.set(deleted: ["abierto", "otro"])
        let sync = removals()
        await sync.bookOpened("abierto")
        try await sync.sync()
        #expect(await local.removedByCloud == ["otro"])
        await sync.bookClosed("abierto")
        #expect(await local.removedByCloud == ["otro", "abierto"])
    }

    @Test("LIB-020: con la app a la vista, un borrado de otro dispositivo llega sin sincronizar")
    func listening() async throws {
        let sync = removals()
        await sync.startListening()
        while await !remote.isObserved { await Task.yield() }
        await remote.emit(["z"])
        for _ in 0..<100 where await local.removedByCloud.isEmpty { await Task.yield() }
        #expect(await local.removedByCloud == ["z"])
        await sync.stopListening()
    }

    @Test("Sin sesión no se envía ni se consulta nada")
    func withoutSession() async throws {
        await local.add("a")
        try await removals(signedIn: false).sync()
        #expect(await remote.calls.isEmpty)
        #expect(await local.pending == ["a"])
    }

    @Test("LIB-020: quitar envía al momento")
    func pushNow() async {
        await local.add("a")
        await removals().push()
        #expect(await remote.calls == ["marcar:a", "archivos:a"])
    }
}
