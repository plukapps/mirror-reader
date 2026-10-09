import Foundation
import Testing
@testable import ReaderDomain

// Mismas reglas que `SyncRemoteBooksUseCase`, `SyncCoversUseCase` y `PositionSync` de Android, solo de bajada.

private actor FakeAccount: AccountRepository {
    var user: AccountUser?
    var failSignIn = false
    private(set) var signIns = 0

    init(user: AccountUser? = nil, failSignIn: Bool = false) {
        self.user = user
        self.failSignIn = failSignIn
    }

    func currentUser() async -> AccountUser? { user }

    func signIn(email: String, password: String) async throws -> AccountUser {
        signIns += 1
        if failSignIn { throw RemoteUnavailableError("sin red") }
        let signedIn = AccountUser(id: "uid", email: email)
        user = signedIn
        return signedIn
    }
}

private struct FakeRemote: RemoteLibrary, RemotePositions {
    var books: [RemoteBook] = []
    var positions: [RemotePosition] = []
    var booksError: Error?
    var positionsError: Error?

    func listBooks() async throws -> [RemoteBook] {
        if let booksError { throw booksError }
        return books
    }

    func listPositions() async throws -> [RemotePosition] {
        if let positionsError { throw positionsError }
        return positions
    }
}

private struct FakeCovers: CoverStore {
    /// Libros con portada en la nube.
    var available: Set<String> = []
    /// Libros cuya bajada falla con este error.
    var failing: [String: RemoteUnavailableError] = [:]

    func downloadCover(bookId: String, to destination: URL) async throws -> Bool {
        if let error = failing[bookId] { throw error }
        return available.contains(bookId)
    }
}

private actor FakeCloudBooks: CloudBooksRepository {
    private(set) var added: [RemoteBook] = []
    private(set) var positions: [RemotePosition] = []
    private(set) var installed: [String] = []
    var withoutCover: [String]

    init(withoutCover: [String] = []) { self.withoutCover = withoutCover }

    func addCloudOnly(_ books: [RemoteBook]) async throws { added += books }

    func applyRemotePositions(_ positions: [RemotePosition]) async throws -> Int {
        self.positions += positions
        return positions.count
    }

    func booksWithoutCover() async throws -> [String] { withoutCover }

    nonisolated func newTempFile() -> URL { FileManager.default.temporaryDirectory.appending(path: UUID().uuidString) }

    func installCover(bookId: String, from file: URL) async throws { installed.append(bookId) }
}

private let book = RemoteBook(id: "a", title: "Walden", authors: ["H. D. Thoreau"], sizeBytes: 10)
private let position = RemotePosition(bookId: "a", locatorJson: "{}", progress: 0.4, readAt: 100)
private let signedIn = AccountUser(id: "uid", email: nil)
private let noCredentials = DevCredentials(email: "", password: "")

struct LibrarySyncTests {
    @Test("LIB-007: con sesión, los libros de la nube se agregan a la biblioteca local")
    func addsCloudBooks() async {
        let cloud = FakeCloudBooks()
        let sync = LibrarySync(
            account: FakeAccount(user: signedIn), credentials: noCredentials,
            remote: FakeRemote(books: [book]), positions: FakeRemote(), covers: FakeCovers(), cloud: cloud
        )

        let outcome = await sync.run()

        #expect(outcome == .synced(books: 1, positions: 0, covers: 0))
        #expect(await cloud.added == [book])
    }

    @Test("ADR 0002: sin sesión ni cuenta de desarrollo no se toca la nube ni la base")
    func noSessionDoesNothing() async {
        let cloud = FakeCloudBooks()
        let sync = LibrarySync(
            account: FakeAccount(), credentials: noCredentials,
            remote: FakeRemote(books: [book]), positions: FakeRemote(), covers: FakeCovers(), cloud: cloud
        )

        #expect(await sync.run() == .noSession)
        #expect(await cloud.added.isEmpty)
    }

    @Test("K-052: sin sesión, inicia sesión con la cuenta de desarrollo y sincroniza")
    func signsInWithDevAccount() async {
        let account = FakeAccount()
        let cloud = FakeCloudBooks()
        let sync = LibrarySync(
            account: account, credentials: DevCredentials(email: "dev@x", password: "secreta"),
            remote: FakeRemote(books: [book]), positions: FakeRemote(), covers: FakeCovers(), cloud: cloud
        )

        #expect(await sync.run() == .synced(books: 1, positions: 0, covers: 0))
        #expect(await account.signIns == 1)
    }

    @Test("K-052: con sesión no vuelve a iniciarla")
    func keepsExistingSession() async {
        let account = FakeAccount(user: signedIn)
        let sync = LibrarySync(
            account: account, credentials: DevCredentials(email: "dev@x", password: "secreta"),
            remote: FakeRemote(), positions: FakeRemote(), covers: FakeCovers(), cloud: FakeCloudBooks()
        )
        _ = await sync.run()
        #expect(await account.signIns == 0)
    }

    @Test("ADR 0002: si el inicio de sesión falla, queda sin sesión y no cambia nada")
    func failedSignIn() async {
        let cloud = FakeCloudBooks()
        let sync = LibrarySync(
            account: FakeAccount(failSignIn: true), credentials: DevCredentials(email: "dev@x", password: "secreta"),
            remote: FakeRemote(books: [book]), positions: FakeRemote(), covers: FakeCovers(), cloud: cloud
        )
        #expect(await sync.run() == .noSession)
        #expect(await cloud.added.isEmpty)
    }

    @Test("ADR 0002: sin conexión no cambia nada")
    func offlineChangesNothing() async {
        let cloud = FakeCloudBooks(withoutCover: ["a"])
        let sync = LibrarySync(
            account: FakeAccount(user: signedIn), credentials: noCredentials,
            remote: FakeRemote(booksError: RemoteUnavailableError("sin red")), positions: FakeRemote(positions: [position]),
            covers: FakeCovers(available: ["a"]), cloud: cloud
        )

        #expect(await sync.run() == .offline)
        #expect(await cloud.added.isEmpty)
        #expect(await cloud.positions.isEmpty)
        #expect(await cloud.installed.isEmpty)
    }

    @Test("SYN-002: baja las posiciones de lectura de la nube")
    func appliesRemotePositions() async {
        let cloud = FakeCloudBooks()
        let sync = LibrarySync(
            account: FakeAccount(user: signedIn), credentials: noCredentials,
            remote: FakeRemote(books: [book]), positions: FakeRemote(positions: [position]), covers: FakeCovers(), cloud: cloud
        )

        #expect(await sync.run() == .synced(books: 1, positions: 1, covers: 0))
        #expect(await cloud.positions == [position])
    }

    @Test("SYN-002: si las posiciones fallan, las portadas igual se bajan")
    func positionsFailureDoesNotStopCovers() async {
        let cloud = FakeCloudBooks(withoutCover: ["a"])
        let sync = LibrarySync(
            account: FakeAccount(user: signedIn), credentials: noCredentials,
            remote: FakeRemote(books: [book]), positions: FakeRemote(positionsError: RemoteUnavailableError("permiso")),
            covers: FakeCovers(available: ["a"]), cloud: cloud
        )

        #expect(await sync.run() == .synced(books: 1, positions: 0, covers: 1))
    }

    @Test("LIB-012: baja las portadas que faltan; sin portada en la nube no instala nada")
    func downloadsMissingCovers() async {
        let cloud = FakeCloudBooks(withoutCover: ["a", "b"])
        let sync = LibrarySync(
            account: FakeAccount(user: signedIn), credentials: noCredentials,
            remote: FakeRemote(), positions: FakeRemote(), covers: FakeCovers(available: ["b"]), cloud: cloud
        )

        #expect(await sync.run() == .synced(books: 0, positions: 0, covers: 1))
        #expect(await cloud.installed == ["b"])
    }

    @Test("LIB-012: una portada que falla no corta las demás")
    func coverFailureDoesNotStopTheRest() async {
        let cloud = FakeCloudBooks(withoutCover: ["a", "b"])
        let covers = FakeCovers(available: ["a", "b"], failing: ["a": RemoteUnavailableError("permiso")])
        let sync = LibrarySync(
            account: FakeAccount(user: signedIn), credentials: noCredentials,
            remote: FakeRemote(), positions: FakeRemote(), covers: covers, cloud: cloud
        )

        _ = await sync.run()
        #expect(await cloud.installed == ["b"])
    }
}

/// Anota en qué momento se avisó del cambio, respecto de las portadas instaladas.
private actor ChangeLog {
    private(set) var coversInstalledAtEachChange: [Int] = []
    func record(_ installed: Int) { coversInstalledAtEachChange.append(installed) }
}

struct LibrarySyncChangeTests {
    @Test("LIB-012: avisa del cambio antes de bajar las portadas y otra vez al terminarlas")
    func notifiesBeforeAndAfterCovers() async {
        let cloud = FakeCloudBooks(withoutCover: ["a"])
        let log = ChangeLog()
        let sync = LibrarySync(
            account: FakeAccount(user: signedIn), credentials: noCredentials,
            remote: FakeRemote(books: [book]), positions: FakeRemote(), covers: FakeCovers(available: ["a"]), cloud: cloud
        )

        _ = await sync.run { await log.record(await cloud.installed.count) }

        #expect(await log.coversInstalledAtEachChange == [0, 1])
    }

    @Test("ADR 0002: sin conexión no avisa de cambios")
    func offlineDoesNotNotify() async {
        let log = ChangeLog()
        let sync = LibrarySync(
            account: FakeAccount(user: signedIn), credentials: noCredentials,
            remote: FakeRemote(booksError: RemoteUnavailableError("sin red")), positions: FakeRemote(), covers: FakeCovers(),
            cloud: FakeCloudBooks()
        )
        _ = await sync.run { await log.record(0) }
        #expect(await log.coversInstalledAtEachChange.isEmpty)
    }
}

struct RemoteMappingTests {
    @Test("LIB-011: los autores de la nube se unen con coma, sin vacíos")
    func joinsAuthors() {
        #expect(RemoteBook(id: "a", title: "T", authors: ["Peter Thiel", " ", "Blake Masters"], sizeBytes: 0).joinedAuthors == "Peter Thiel, Blake Masters")
        #expect(RemoteBook(id: "a", title: "T", authors: [], sizeBytes: 0).joinedAuthors == nil)
    }

    @Test("RDR-005: progresión a porcentaje, como Android")
    func progressToPercent() {
        #expect(progressPercent(nil) == nil)
        #expect(progressPercent(0.425) == 43)
        #expect(progressPercent(1.2) == 100)
        #expect(progressPercent(-0.1) == 0)
    }

    @Test("SYN-003: gana la lectura más reciente; en empate se conserva la local")
    func newestReadWins() {
        #expect(shouldUseRemotePosition(localReadAt: nil, remoteReadAt: 1))
        #expect(shouldUseRemotePosition(localReadAt: 1, remoteReadAt: 2))
        #expect(!shouldUseRemotePosition(localReadAt: 2, remoteReadAt: 1))
        #expect(!shouldUseRemotePosition(localReadAt: 2, remoteReadAt: 2))
    }
}
