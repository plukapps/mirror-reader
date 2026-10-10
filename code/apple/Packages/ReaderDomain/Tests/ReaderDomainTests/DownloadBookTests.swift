import Foundation
import Testing
@testable import ReaderDomain

// Mismas reglas que `DownloadBookUseCase` de Android (LIB-007).

private actor FakeBookFiles: BookFiles {
    var downloaded: Set<String>
    private(set) var installed: [String] = []
    var failInstall = false

    init(downloaded: Set<String> = []) { self.downloaded = downloaded }

    func isDownloaded(bookId: String) async -> Bool { downloaded.contains(bookId) }

    nonisolated func newTempFile() -> URL { FileManager.default.temporaryDirectory.appending(path: UUID().uuidString) }

    func installBook(bookId: String, from file: URL) async throws {
        if failInstall { throw CocoaError(.fileWriteUnknown) }
        installed.append(bookId)
        downloaded.insert(bookId)
    }

    func setFailInstall() { failInstall = true }
}

private struct FakeBookStore: BookFileStore {
    var error: Error?
    func downloadBook(bookId: String, to destination: URL) async throws {
        if let error { throw error }
    }

    func uploadBook(bookId: String, from file: URL) async throws {}
}

struct DownloadBookTests {
    @Test func alreadyDownloadedDoesNothing() async throws {
        let files = FakeBookFiles(downloaded: ["a"])
        try await DownloadBook(files: files, store: FakeBookStore(error: CocoaError(.fileNoSuchFile)))(bookId: "a")
        #expect(await files.installed.isEmpty)
    }

    @Test func cloudOnlyBookIsDownloadedAndInstalled() async throws {
        let files = FakeBookFiles()
        try await DownloadBook(files: files, store: FakeBookStore())(bookId: "a")
        #expect(await files.installed == ["a"])
    }

    @Test func withoutConnectionSaysSo() async {
        let files = FakeBookFiles()
        let download = DownloadBook(files: files, store: FakeBookStore(error: RemoteUnavailableError("sin red")))
        await #expect(throws: BookOpenError("Sin conexión. No se pudo descargar el libro.")) {
            try await download(bookId: "a")
        }
        #expect(await files.installed.isEmpty)
    }

    @Test func otherFailuresGiveAGenericMessage() async {
        let files = FakeBookFiles()
        await files.setFailInstall()
        await #expect(throws: BookOpenError("No se pudo descargar el libro.")) {
            try await DownloadBook(files: files, store: FakeBookStore())(bookId: "a")
        }
    }
}
