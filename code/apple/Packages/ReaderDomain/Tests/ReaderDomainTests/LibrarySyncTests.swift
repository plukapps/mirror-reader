import Foundation
import Testing
@testable import ReaderDomain

// Mismos casos que `SyncLibraryUseCaseTest` y `SyncCoversUseCaseTest` de Android, más el inicio de sesión con la
// cuenta de desarrollo, propio de Apple.

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

/// La nube: metadatos, archivos y portadas, con errores inyectables.
private actor FakeCloud: RemoteLibrary, BookFileStore, CoverStore, QuotaSource {
    var books: [RemoteBook] = []
    var booksError: (any Error)?
    /// Libros con portada en la nube.
    var coversAvailable: Set<String> = []
    var coverDownloadErrors: [String: any Error] = [:]
    var coverUploadError: (any Error)?
    var downloadErrors: [String: any Error] = [:]
    private(set) var uploadedFiles: [String] = []
    private(set) var savedBooks: [String] = []
    private(set) var uploadedCovers: [String] = []
    private(set) var downloadedFiles: [String] = []

    init(books: [RemoteBook] = [], booksError: (any Error)? = nil, coversAvailable: Set<String> = []) {
        self.books = books
        self.booksError = booksError
        self.coversAvailable = coversAvailable
    }

    func set(coverDownloadErrors: [String: any Error] = [:], coverUploadError: (any Error)? = nil, downloadErrors: [String: any Error] = [:]) {
        self.coverDownloadErrors = coverDownloadErrors
        self.coverUploadError = coverUploadError
        self.downloadErrors = downloadErrors
    }

    func listBooks() async throws -> [RemoteBook] {
        if let booksError { throw booksError }
        return books
    }

    func saveBook(_ book: RemoteBook) async throws { savedBooks.append(book.id) }

    func uploadBook(bookId: String, from file: URL) async throws { uploadedFiles.append(bookId) }

    func downloadBook(bookId: String, to destination: URL) async throws {
        if let error = downloadErrors[bookId] { throw error }
        downloadedFiles.append(bookId)
    }

    func uploadCover(bookId: String, from file: URL) async throws {
        if let coverUploadError { throw coverUploadError }
        uploadedCovers.append(bookId)
    }

    func downloadCover(bookId: String, to destination: URL) async throws -> Bool {
        if let error = coverDownloadErrors[bookId] { throw error }
        return coversAvailable.contains(bookId)
    }

    func current() async throws -> StorageQuota { StorageQuota(usedBytes: 0, quotaBytes: 1_000_000) }
}

/// La base local: libros solo en la nube, pendientes de subir y portadas.
private actor FakeLocal: CloudBooksRepository, BookUploadRepository, BookFiles {
    private(set) var added: [RemoteBook] = []
    private(set) var installedCovers: [String] = []
    private(set) var markedCovers: [String] = []
    private(set) var installedBooks: [String] = []
    private(set) var markedUploaded: [String] = []
    var cloudOnly: [String]
    var withoutCover: [String]
    var coversPending: [PendingCover]
    var pendingUploads: [PendingUpload]

    init(
        cloudOnly: [String] = [],
        withoutCover: [String] = [],
        coversPending: [PendingCover] = [],
        pendingUploads: [PendingUpload] = []
    ) {
        self.cloudOnly = cloudOnly
        self.withoutCover = withoutCover
        self.coversPending = coversPending
        self.pendingUploads = pendingUploads
    }

    func addCloudOnly(_ books: [RemoteBook]) async throws {
        added += books
        cloudOnly += books.map(\.id).filter { !cloudOnly.contains($0) && !installedBooks.contains($0) }
    }

    func cloudOnlyBookIds() async throws -> [String] { cloudOnly }

    func coversToUpload() async throws -> [PendingCover] { coversPending }

    func markCoverUploaded(bookId: String) async throws {
        markedCovers.append(bookId)
        coversPending.removeAll { $0.bookId == bookId }
    }

    func booksWithoutCover() async throws -> [String] { withoutCover }

    nonisolated func newTempFile() -> URL { FileManager.default.temporaryDirectory.appending(path: UUID().uuidString) }

    func installCover(bookId: String, from file: URL) async throws {
        installedCovers.append(bookId)
        withoutCover.removeAll { $0 == bookId }
    }

    func pending() async throws -> [PendingUpload] { pendingUploads }

    func markUploaded(bookId: String, sizeBytes: Int64, coverUploaded: Bool) async throws {
        markedUploaded.append(bookId)
        pendingUploads.removeAll { $0.book.id == bookId }
    }

    func isDownloaded(bookId: String) async -> Bool { installedBooks.contains(bookId) }

    func installBook(bookId: String, from file: URL) async throws {
        installedBooks.append(bookId)
        cloudOnly.removeAll { $0 == bookId }
    }
}

private actor FakeFlusher: PositionFlusher {
    private(set) var flushes = 0
    /// Lo que había subido cuando se envió la posición: debe salir después de los libros (SYN-011).
    private(set) var uploadedAtFlush: [String] = []
    let local: FakeLocal?

    init(local: FakeLocal? = nil) { self.local = local }

    func flush() async {
        flushes += 1
        if let local { uploadedAtFlush = await local.markedUploaded }
    }
}

private let book = RemoteBook(id: "a", title: "Walden", authors: ["H. D. Thoreau"], sizeBytes: 10)
private let signedIn = AccountUser(id: "uid", email: nil)
private let noCredentials = DevCredentials(email: "", password: "")
private let offline = RemoteUnavailableError("sin red")
private struct Boom: Error {}

private func pendingUpload(_ id: String) -> PendingUpload {
    PendingUpload(book: RemoteBook(id: id, title: "T\(id)", authors: [], sizeBytes: 1), file: URL(filePath: "/libros/\(id).epub"))
}

private func makeSync(
    account: FakeAccount = FakeAccount(user: signedIn),
    credentials: DevCredentials = noCredentials,
    cloud: FakeCloud = FakeCloud(),
    local: FakeLocal = FakeLocal(),
    flusher: FakeFlusher = FakeFlusher()
) -> LibrarySync {
    LibrarySync(
        account: account,
        credentials: credentials,
        remote: cloud,
        covers: cloud,
        cloud: local,
        upload: UploadBooks(uploads: local, files: cloud, covers: cloud, library: cloud, quota: cloud),
        download: DownloadBook(files: local, store: cloud),
        positions: flusher
    )
}

private extension LibrarySyncOutcome {
    var report: SyncReport? {
        if case let .done(report) = self { return report }
        return nil
    }
}

struct LibrarySyncSessionTests {
    @Test("ADR 0002: sin sesión ni cuenta de desarrollo no se toca la nube ni la base")
    func noSessionDoesNothing() async {
        let local = FakeLocal()
        let sync = makeSync(account: FakeAccount(), cloud: FakeCloud(books: [book]), local: local)
        #expect(await sync.run() == .noSession)
        #expect(await local.added.isEmpty)
    }

    @Test("K-052: sin sesión, inicia sesión con la cuenta de desarrollo y sincroniza")
    func signsInWithDevAccount() async {
        let account = FakeAccount()
        let local = FakeLocal()
        let sync = makeSync(
            account: account, credentials: DevCredentials(email: "dev@x", password: "secreta"),
            cloud: FakeCloud(books: [book]), local: local
        )
        #expect(await sync.run().report != nil)
        #expect(await account.signIns == 1)
        #expect(await local.added == [book])
    }

    @Test("K-052: con sesión no vuelve a iniciarla")
    func keepsExistingSession() async {
        let account = FakeAccount(user: signedIn)
        _ = await makeSync(account: account, credentials: DevCredentials(email: "dev@x", password: "secreta")).run()
        #expect(await account.signIns == 0)
    }

    @Test("ADR 0002: si el inicio de sesión falla, queda sin sesión y no cambia nada")
    func failedSignIn() async {
        let local = FakeLocal()
        let sync = makeSync(
            account: FakeAccount(failSignIn: true), credentials: DevCredentials(email: "dev@x", password: "secreta"),
            cloud: FakeCloud(books: [book]), local: local
        )
        #expect(await sync.run() == .noSession)
        #expect(await local.added.isEmpty)
    }
}

struct LibrarySyncTests {
    @Test("LIB-007: los libros de la nube se agregan y se bajan al dispositivo")
    func addsAndDownloadsCloudBooks() async {
        let cloud = FakeCloud(books: [book])
        let local = FakeLocal()
        let report = await makeSync(cloud: cloud, local: local).run().report
        #expect(report == SyncReport(downloaded: 1))
        #expect(await local.added == [book])
        #expect(await local.installedBooks == ["a"])
    }

    @Test("SYN-001: sube los pendientes y baja los que faltan")
    func uploadsAndDownloads() async {
        let cloud = FakeCloud(books: [book])
        let local = FakeLocal(pendingUploads: [pendingUpload("b")])
        let report = await makeSync(cloud: cloud, local: local).run().report
        #expect(report == SyncReport(upload: UploadReport(uploaded: 1), downloaded: 1))
        #expect(await cloud.uploadedFiles == ["b"])
        #expect(await local.installedBooks == ["a"])
    }

    @Test("SYN-001: una segunda pasada no hace nada")
    func secondPassIsNoOp() async {
        let cloud = FakeCloud(books: [book])
        let local = FakeLocal(pendingUploads: [pendingUpload("b")])
        let sync = makeSync(cloud: cloud, local: local)
        _ = await sync.run()
        #expect(await sync.run().report == SyncReport())
        #expect(await cloud.uploadedFiles == ["b"])
        #expect(await cloud.downloadedFiles == ["a"])
    }

    @Test("SYN-008: sin conexión no cambia nada y todo queda pendiente")
    func offlineKeepsEverythingPending() async {
        let cloud = FakeCloud(books: [book], booksError: offline, coversAvailable: ["a"])
        let local = FakeLocal(cloudOnly: ["c"], withoutCover: ["c"], pendingUploads: [pendingUpload("b")])
        let flusher = FakeFlusher()
        let report = await makeSync(cloud: cloud, local: local, flusher: flusher).run().report
        #expect(report == SyncReport(unreachable: true))
        #expect(report?.issue == .offline)
        #expect(await local.added.isEmpty)
        #expect(await cloud.uploadedFiles.isEmpty)
        #expect(await local.installedBooks.isEmpty)
        #expect(await flusher.flushes == 0)
    }

    @Test("SYN-008: si se corta la conexión a mitad de la bajada, queda lo que se bajó")
    func offlineMidDownloadKeepsWhatWasDownloaded() async {
        let cloud = FakeCloud()
        await cloud.set(downloadErrors: ["b": offline])
        let local = FakeLocal(cloudOnly: ["a", "b", "c"])
        let report = await makeSync(cloud: cloud, local: local).run().report
        #expect(report == SyncReport(downloaded: 1, unreachable: true))
        #expect(await local.installedBooks == ["a"])
    }

    @Test("LIB-007: un libro que no se puede bajar no frena a los demás")
    func failedDownloadContinues() async {
        let cloud = FakeCloud()
        await cloud.set(downloadErrors: ["a": Boom()])
        let local = FakeLocal(cloudOnly: ["a", "b"])
        let report = await makeSync(cloud: cloud, local: local).run().report
        #expect(report == SyncReport(downloaded: 1, downloadFailed: 1))
        #expect(report?.issue == .failed(1))
        #expect(await local.installedBooks == ["b"])
    }

    @Test("SYN-008: el problema se informa con prioridad sin conexión, fallos y falta de espacio")
    func issuePriority() {
        #expect(SyncReport(upload: UploadReport(notEnoughSpace: 2, failed: 1), unreachable: true).issue == .offline)
        #expect(SyncReport(upload: UploadReport(notEnoughSpace: 2, failed: 1), downloadFailed: 1).issue == .failed(2))
        #expect(SyncReport(upload: UploadReport(notEnoughSpace: 2)).issue == .notEnoughSpace(2))
        #expect(SyncReport(upload: UploadReport(uploaded: 1), downloaded: 3).issue == nil)
    }

    @Test("SYN-011: las posiciones pendientes salen al final, con los libros ya subidos")
    func flushesPositionsLast() async {
        let local = FakeLocal(pendingUploads: [pendingUpload("b")])
        let flusher = FakeFlusher(local: local)
        _ = await makeSync(local: local, flusher: flusher).run()
        #expect(await flusher.flushes == 1)
        #expect(await flusher.uploadedAtFlush == ["b"])
    }

    @Test("SYN-011: una pasada sin conexión en la subida no intenta enviar posiciones")
    func offlineUploadSkipsPositions() async {
        let cloud = FakeCloud()
        let local = FakeLocal(pendingUploads: [pendingUpload("b")])
        let flusher = FakeFlusher()
        let sync = LibrarySync(
            account: FakeAccount(user: signedIn), credentials: noCredentials, remote: cloud, covers: cloud, cloud: local,
            upload: UploadBooks(uploads: local, files: cloud, covers: cloud, library: cloud, quota: OfflineQuota()),
            download: DownloadBook(files: local, store: cloud), positions: flusher
        )
        let report = await sync.run().report
        #expect(report?.offline == true)
        #expect(await flusher.flushes == 0)
    }
}

private struct OfflineQuota: QuotaSource {
    func current() async throws -> StorageQuota { throw RemoteUnavailableError("sin red") }
}

struct LibrarySyncCoverTests {
    @Test("LIB-012: sube las portadas pendientes una sola vez")
    func uploadsPendingCovers() async {
        let cloud = FakeCloud()
        let local = FakeLocal(coversPending: [PendingCover(bookId: "a", file: URL(filePath: "/portadas/a.jpg"))])
        let sync = makeSync(cloud: cloud, local: local)
        let report = await sync.run().report
        _ = await sync.run()
        #expect(report?.coversUploaded == 1)
        #expect(await cloud.uploadedCovers == ["a"])
        #expect(await local.markedCovers == ["a"])
    }

    @Test("LIB-012: una portada que no se pudo subir se reintenta en la próxima pasada")
    func retriesFailedCoverUpload() async {
        let cloud = FakeCloud()
        await cloud.set(coverUploadError: Boom())
        let local = FakeLocal(coversPending: [PendingCover(bookId: "a", file: URL(filePath: "/portadas/a.jpg"))])
        let sync = makeSync(cloud: cloud, local: local)
        _ = await sync.run()
        #expect(await local.markedCovers.isEmpty)
        await cloud.set()
        _ = await sync.run()
        #expect(await local.markedCovers == ["a"])
    }

    @Test("LIB-012: baja las portadas que faltan; sin portada en la nube no instala nada")
    func downloadsMissingCovers() async {
        let cloud = FakeCloud(coversAvailable: ["b"])
        let local = FakeLocal(withoutCover: ["a", "b"])
        let report = await makeSync(cloud: cloud, local: local).run().report
        #expect(report?.coversDownloaded == 1)
        #expect(await local.installedCovers == ["b"])
    }

    @Test("LIB-012: una portada que falla no corta las demás")
    func coverFailureDoesNotStopTheRest() async {
        let cloud = FakeCloud(coversAvailable: ["a", "b"])
        await cloud.set(coverDownloadErrors: ["a": Boom()])
        let local = FakeLocal(withoutCover: ["a", "b"])
        _ = await makeSync(cloud: cloud, local: local).run()
        #expect(await local.installedCovers == ["b"])
    }

    @Test("LIB-012: sin conexión las portadas se cortan, pero la pasada sigue con los libros")
    func offlineCoversStop() async {
        let cloud = FakeCloud(coversAvailable: ["a", "b"])
        await cloud.set(coverDownloadErrors: ["a": offline])
        let local = FakeLocal(withoutCover: ["a", "b"])
        _ = await makeSync(cloud: cloud, local: local).run()
        #expect(await local.installedCovers.isEmpty)
    }
}

/// Anota cuántas portadas había instaladas cada vez que se avisó un cambio.
private actor ChangeLog {
    private(set) var coversInstalledAtEachChange: [Int] = []
    func record(_ installed: Int) { coversInstalledAtEachChange.append(installed) }
}

struct LibrarySyncChangeTests {
    @Test("LIB-012: avisa del cambio antes de bajar las portadas y otra vez al terminarlas")
    func notifiesBeforeAndAfterCovers() async {
        let local = FakeLocal(withoutCover: ["x"])
        let log = ChangeLog()
        let sync = makeSync(cloud: FakeCloud(coversAvailable: ["x"]), local: local)
        _ = await sync.run { await log.record(await local.installedCovers.count) }
        #expect(await log.coversInstalledAtEachChange.first == 0)
        #expect(await log.coversInstalledAtEachChange.last == 1)
    }

    @Test("LIB-007: avisa del cambio tras cada libro bajado")
    func notifiesAfterEachDownload() async {
        let local = FakeLocal(cloudOnly: ["a", "b"])
        let log = ChangeLog()
        _ = await makeSync(local: local).run { await log.record(0) }
        // Libros de la nube, y uno por cada descarga.
        #expect(await log.coversInstalledAtEachChange.count == 3)
    }

    @Test("ADR 0002: sin conexión no avisa de cambios")
    func offlineDoesNotNotify() async {
        let log = ChangeLog()
        _ = await makeSync(cloud: FakeCloud(booksError: offline)).run { await log.record(0) }
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
}
