import Foundation
import Testing
@testable import ReaderDomain

// Mismos casos que `UploadBooksUseCaseTest` de Android.

private let mib: Int64 = 1024 * 1024

private func pending(_ id: String, _ sizeBytes: Int64, withCover: Bool = false) -> PendingUpload {
    PendingUpload(
        book: RemoteBook(id: id, title: "T\(id)", authors: ["A"], sizeBytes: sizeBytes),
        file: URL(filePath: "/libros/\(id).epub"),
        cover: withCover ? URL(filePath: "/portadas/\(id).jpg") : nil
    )
}

/// Registro común del orden de las llamadas.
private actor Calls {
    private(set) var all: [String] = []
    func add(_ call: String) { all.append(call) }
}

private actor FakeUploads: BookUploadRepository {
    let items: [PendingUpload]
    let calls: Calls
    private(set) var marked: [String: Int64] = [:]
    private(set) var markedOrder: [String] = []
    private(set) var coverFlags: [String: Bool] = [:]

    init(_ items: [PendingUpload], calls: Calls) {
        self.items = items
        self.calls = calls
    }

    func pending() async throws -> [PendingUpload] { items }

    func markUploaded(bookId: String, sizeBytes: Int64, coverUploaded: Bool) async throws {
        await calls.add("marcar:\(bookId)")
        marked[bookId] = sizeBytes
        markedOrder.append(bookId)
        coverFlags[bookId] = coverUploaded
    }
}

private struct FakeFiles: BookFileStore, CoverStore {
    let calls: Calls
    var fileErrors: [String: any Error] = [:]
    var coverError: (any Error)?

    func uploadBook(bookId: String, from file: URL) async throws {
        await calls.add("archivo:\(bookId)")
        if let error = fileErrors[bookId] { throw error }
    }

    func downloadBook(bookId: String, to destination: URL) async throws {}

    func uploadCover(bookId: String, from file: URL) async throws {
        await calls.add("portada:\(bookId)")
        if let coverError { throw coverError }
    }

    func downloadCover(bookId: String, to destination: URL) async throws -> Bool { false }
}

private struct FakeRemote: RemoteLibrary {
    let calls: Calls
    var failFor: [String: any Error] = [:]

    func listBooks() async throws -> [RemoteBook] { [] }

    func saveBook(_ book: RemoteBook) async throws {
        await calls.add("metadatos:\(book.id)")
        if let error = failFor[book.id] { throw error }
    }
}

private struct FakeQuota: QuotaSource {
    var quota: StorageQuota?

    func current() async throws -> StorageQuota {
        guard let quota else { throw RemoteUnavailableError("sin red") }
        return quota
    }
}

private struct Boom: Error {}

private func quota(used: Int64 = 0, limit: Int64 = 15 * mib) -> FakeQuota {
    FakeQuota(quota: StorageQuota(usedBytes: used, quotaBytes: limit))
}

private func useCase(
    _ uploads: FakeUploads,
    files: FakeFiles,
    remote: FakeRemote,
    quota: FakeQuota = quota()
) -> UploadBooks {
    UploadBooks(uploads: uploads, files: files, covers: files, library: remote, quota: quota)
}

struct UploadBooksTests {
    fileprivate let calls = Calls()

    @Test("SYN-001: el archivo va primero, los metadatos después y al final se marca el libro")
    func fileThenMetadataThenMark() async {
        let uploads = FakeUploads([pending("a", mib)], calls: calls)
        let report = await useCase(uploads, files: FakeFiles(calls: calls), remote: FakeRemote(calls: calls))()
        #expect(report == UploadReport(uploaded: 1))
        #expect(await calls.all == ["archivo:a", "metadatos:a", "marcar:a"])
        #expect(await uploads.marked == ["a": mib])
    }

    @Test("LIB-012: la portada sube después de los metadatos y antes de marcar el libro")
    func coverBeforeMark() async {
        let uploads = FakeUploads([pending("a", mib, withCover: true)], calls: calls)
        let report = await useCase(uploads, files: FakeFiles(calls: calls), remote: FakeRemote(calls: calls))()
        #expect(report == UploadReport(uploaded: 1))
        #expect(await calls.all == ["archivo:a", "metadatos:a", "portada:a", "marcar:a"])
        #expect(await uploads.coverFlags == ["a": true])
    }

    @Test("LIB-012: un libro sin portada no la sube")
    func noCover() async {
        let uploads = FakeUploads([pending("a", mib)], calls: calls)
        _ = await useCase(uploads, files: FakeFiles(calls: calls), remote: FakeRemote(calls: calls))()
        #expect(await !calls.all.contains("portada:a"))
        #expect(await uploads.coverFlags == ["a": false])
    }

    @Test("LIB-012: un rechazo de la portada no impide que el libro quede subido")
    func coverRejection() async {
        let uploads = FakeUploads([pending("a", mib, withCover: true)], calls: calls)
        let files = FakeFiles(calls: calls, coverError: Boom())
        let report = await useCase(uploads, files: files, remote: FakeRemote(calls: calls))()
        #expect(report == UploadReport(uploaded: 1))
        #expect(await uploads.marked == ["a": mib])
        #expect(await uploads.coverFlags == ["a": false])
    }

    @Test("SYN-001: sin conexión al subir la portada el libro queda pendiente")
    func offlineDuringCover() async {
        let uploads = FakeUploads([pending("a", mib, withCover: true)], calls: calls)
        let files = FakeFiles(calls: calls, coverError: RemoteUnavailableError("sin red"))
        let report = await useCase(uploads, files: files, remote: FakeRemote(calls: calls))()
        #expect(report.unreachable)
        #expect(await uploads.marked.isEmpty)
    }

    @Test("SYN-001: sin pendientes no hace nada")
    func nothingPending() async {
        let uploads = FakeUploads([], calls: calls)
        let report = await useCase(uploads, files: FakeFiles(calls: calls), remote: FakeRemote(calls: calls))()
        #expect(report == UploadReport())
        #expect(report.nothingToDo)
        #expect(await calls.all.isEmpty)
    }

    @Test("SYN-001: un archivo que falla deja el libro pendiente y no frena a los demás")
    func failedFileContinues() async {
        let uploads = FakeUploads([pending("a", mib), pending("b", mib)], calls: calls)
        let files = FakeFiles(calls: calls, fileErrors: ["a": Boom()])
        let report = await useCase(uploads, files: files, remote: FakeRemote(calls: calls))()
        #expect(report == UploadReport(uploaded: 1, failed: 1))
        #expect(await uploads.marked == ["b": mib])
        #expect(await !calls.all.contains("metadatos:a"))
    }

    @Test("SYN-001: metadatos que fallan dejan el libro pendiente")
    func failedMetadata() async {
        let uploads = FakeUploads([pending("a", mib), pending("b", mib)], calls: calls)
        let remote = FakeRemote(calls: calls, failFor: ["a": Boom()])
        let report = await useCase(uploads, files: FakeFiles(calls: calls), remote: remote)()
        #expect(report == UploadReport(uploaded: 1, failed: 1))
        #expect(await uploads.marked == ["b": mib])
    }

    @Test("SYN-008: sin conexión se corta la tanda y todo queda pendiente")
    func offlineStopsRun() async {
        let uploads = FakeUploads([pending("a", mib), pending("b", mib)], calls: calls)
        let files = FakeFiles(calls: calls, fileErrors: ["a": RemoteUnavailableError("sin red")])
        let report = await useCase(uploads, files: files, remote: FakeRemote(calls: calls))()
        #expect(report == UploadReport(unreachable: true))
        #expect(await uploads.marked.isEmpty)
        #expect(await calls.all == ["archivo:a"])
    }

    @Test("SYN-008: sin conexión al escribir los metadatos también se corta la tanda")
    func offlineDuringMetadata() async {
        let uploads = FakeUploads([pending("a", mib), pending("b", mib)], calls: calls)
        let remote = FakeRemote(calls: calls, failFor: ["a": RemoteUnavailableError("sin red")])
        let report = await useCase(uploads, files: FakeFiles(calls: calls), remote: remote)()
        #expect(report.unreachable)
        #expect(await uploads.marked.isEmpty)
        #expect(await !calls.all.contains("archivo:b"))
    }

    @Test("LIB-009: si no se puede leer la cuota no se sube nada")
    func unreadableQuota() async {
        let uploads = FakeUploads([pending("a", mib)], calls: calls)
        let report = await useCase(
            uploads, files: FakeFiles(calls: calls), remote: FakeRemote(calls: calls), quota: FakeQuota(quota: nil)
        )()
        #expect(report == UploadReport(unreachable: true))
        #expect(await calls.all.isEmpty)
    }

    @Test("LIB-009: lo que no entra en el espacio libre no se sube, lo que entra sí")
    func skipsWhatDoesNotFit() async {
        let uploads = FakeUploads([pending("a", 3 * mib), pending("b", 3 * mib), pending("c", mib)], calls: calls)
        let report = await useCase(
            uploads, files: FakeFiles(calls: calls), remote: FakeRemote(calls: calls),
            quota: quota(used: 10 * mib, limit: 15 * mib)
        )()
        #expect(report == UploadReport(uploaded: 2, notEnoughSpace: 1))
        #expect(await uploads.markedOrder == ["a", "c"])
        #expect(await !calls.all.contains("archivo:b"))
    }

    @Test("LIB-009: el espacio libre baja con cada subida aunque el servidor aún no lo refleje")
    func spaceUsedInRunCounts() async {
        let uploads = FakeUploads([pending("a", 4 * mib), pending("b", 4 * mib)], calls: calls)
        let report = await useCase(
            uploads, files: FakeFiles(calls: calls), remote: FakeRemote(calls: calls),
            quota: quota(used: 0, limit: 6 * mib)
        )()
        #expect(report == UploadReport(uploaded: 1, notEnoughSpace: 1))
    }

    @Test("LIB-009: el rechazo de cuota del servidor cuenta como sin espacio y sigue")
    func serverQuotaRejection() async {
        let uploads = FakeUploads([pending("a", mib), pending("b", mib)], calls: calls)
        let files = FakeFiles(calls: calls, fileErrors: ["a": QuotaExceededError()])
        let report = await useCase(uploads, files: files, remote: FakeRemote(calls: calls))()
        #expect(report == UploadReport(uploaded: 1, notEnoughSpace: 1))
    }
}
