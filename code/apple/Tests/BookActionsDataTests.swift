import CoreGraphics
import Foundation
import ImageIO
import ReaderDomain
import Testing
import UniformTypeIdentifiers
@testable import Reader

// Datos de las acciones del libro con SwiftData en memoria y carpetas temporales. Mismos casos que `BookEditorTest`
// de Android.
struct BookActionsDataTests {
    private let files = LibraryFiles(root: FileManager.default.temporaryDirectory.appending(path: "BookActionsDataTests-\(UUID().uuidString)"))

    private func makeStore() throws -> LibraryStore {
        LibraryStore(container: try LibraryStore.inMemoryContainer(), files: files, now: { Date(timeIntervalSince1970: 9_000) })
    }

    private func remote(_ id: String) -> RemoteBook {
        RemoteBook(id: id, title: "Drácula", authors: ["Bram Stoker"], sizeBytes: 7)
    }

    /// Un libro importado con archivo y portada en disco.
    private func imported(_ store: LibraryStore, _ id: String = "d") async throws {
        try FileManager.default.createDirectory(at: files.booksDirectory, withIntermediateDirectories: true)
        try FileManager.default.createDirectory(at: files.coversDirectory, withIntermediateDirectories: true)
        try Data("epub".utf8).write(to: files.bookFile(bookId: id))
        try Data("jpg".utf8).write(to: files.coverFile(bookId: id))
        try await store.addImported(id: id, title: "Drácula", author: "Bram Stoker", hasCover: true, sizeBytes: 4)
    }

    /// PNG de `width` × `height` en memoria.
    private func png(width: Int, height: Int) throws -> Data {
        let context = try #require(
            CGContext(
                data: nil, width: width, height: height, bitsPerComponent: 8, bytesPerRow: 0,
                space: CGColorSpaceCreateDeviceRGB(), bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue
            )
        )
        context.setFillColor(red: 0.5, green: 0.1, blue: 0.1, alpha: 1)
        context.fill(CGRect(x: 0, y: 0, width: width, height: height))
        let image = try #require(context.makeImage())
        let data = NSMutableData()
        let destination = try #require(CGImageDestinationCreateWithData(data, UTType.png.identifier as CFString, 1, nil))
        CGImageDestinationAddImage(destination, image, nil)
        #expect(CGImageDestinationFinalize(destination))
        return data as Data
    }

    @Test("LIB-018: marcar terminado y no leído cambia el estado del libro")
    func finishedAndUnreadStatus() async throws {
        let store = try makeStore()
        try await imported(store)
        let actions = BookActions(positions: store, editor: store, requestFlush: {})

        await store.save(bookId: "d", position: SavedPosition(locatorJson: #"{"href":"c3.xhtml"}"#, progress: 0.42))
        await actions.markFinished(bookId: "d")
        #expect(await store.books()[0].status == .finished)

        await actions.markUnread(bookId: "d")
        let book = await store.books()[0]
        #expect(book.status == .new)
        #expect(book.lastReadAt != nil)
        #expect(await store.pendingPositions().isEmpty) // Sin subir: todavía no hay nada que enviar.
    }

    @Test("LIB-020: quitar borra el libro, su posición y sus archivos")
    func removeDeletesEverything() async throws {
        let store = try makeStore()
        try await imported(store)
        await store.save(bookId: "d", position: SavedPosition(locatorJson: #"{"href":"c1"}"#, progress: 0.4))

        await store.remove(bookId: "d")

        #expect(await store.books().isEmpty)
        #expect(await store.position(bookId: "d") == nil)
        #expect(!FileManager.default.fileExists(atPath: files.bookFile(bookId: "d").path))
        #expect(!FileManager.default.fileExists(atPath: files.coverFile(bookId: "d").path))
    }

    @Test("LIB-020: la sincronización no vuelve a traer un libro quitado")
    func cloudDoesNotBringItBack() async throws {
        let store = try makeStore()
        try await store.addCloudOnly([remote("quitado"), remote("otro")])
        await store.remove(bookId: "quitado")

        try await store.addCloudOnly([remote("quitado"), remote("otro")])

        #expect(await store.books().map(\.id) == ["otro"])
        #expect(try await store.cloudOnlyBookIds() == ["otro"])
    }

    @Test("LIB-020: un libro de la nube queda pendiente de borrar allí; uno nunca subido, no")
    func onlyUploadedBooksArePending() async throws {
        let store = try makeStore()
        try await store.addCloudOnly([remote("subido")])
        try await imported(store, "local")

        await store.remove(bookId: "subido")
        await store.remove(bookId: "local")

        #expect(await store.pendingCloudRemovals() == ["subido"])
        await store.markCloudRemoved(bookId: "subido")
        #expect(await store.pendingCloudRemovals().isEmpty)
    }

    @Test("LIB-008: un borrado de otro dispositivo quita aquí solo los libros que ya estaban en la nube")
    func deletedInCloudRemovesOnlyUploaded() async throws {
        let store = try makeStore()
        try await store.addCloudOnly([remote("subido")])
        try await imported(store, "local")

        await store.removeDeletedInCloud(bookIds: ["subido", "local", "desconocido"])

        #expect(await store.books().map(\.id) == ["local"])
        #expect(await store.pendingCloudRemovals().isEmpty)
    }

    @Test("LIB-020: un libro borrado que otro dispositivo vuelve a subir aparece de nuevo")
    func revivedInCloudComesBack() async throws {
        let store = try makeStore()
        try await store.addCloudOnly([remote("revive")])
        await store.removeDeletedInCloud(bookIds: ["revive"])
        #expect(await store.books().isEmpty)

        // Otro dispositivo lo reimportó: la nube lo lista vivo otra vez.
        try await store.addCloudOnly([remote("revive")])

        #expect(await store.books().map(\.id) == ["revive"])
    }

    @Test("LIB-020: un libro quitado aquí que todavía no se borró de la nube no vuelve")
    func pendingRemovalStillBlocks() async throws {
        let store = try makeStore()
        try await store.addCloudOnly([remote("pendiente")])
        await store.remove(bookId: "pendiente")

        try await store.addCloudOnly([remote("pendiente")])

        #expect(await store.books().isEmpty)
        #expect(await store.pendingCloudRemovals() == ["pendiente"])
    }

    @Test("LIB-020: importar el mismo archivo lo devuelve a la biblioteca")
    func importingAgainRestores() async throws {
        let store = try makeStore()
        try await imported(store)
        await store.remove(bookId: "d")

        try await imported(store)
        try await store.addCloudOnly([remote("d")])

        #expect(await store.books().map(\.id) == ["d"])
        #expect(await store.books()[0].isDownloaded)
    }

    @Test("LIB-019: la foto elegida reemplaza la portada, achicada, y cambia su versión")
    func replaceCover() async throws {
        let store = try makeStore()
        try await imported(store)

        #expect(await store.replaceCover(bookId: "d", imageData: try png(width: 800, height: 1200)))

        let book = await store.books()[0]
        #expect(book.coverVersion != 0)
        let source = try #require(CGImageSourceCreateWithURL(files.coverFile(bookId: "d") as CFURL, nil))
        let properties = try #require(CGImageSourceCopyPropertiesAtIndex(source, 0, nil) as? [CFString: Any])
        #expect(properties[kCGImagePropertyPixelHeight] as? Int == 600)
        #expect(CGImageSourceGetType(source) as String? == UTType.jpeg.identifier)
    }

    @Test("LIB-019: datos que no son una imagen no cambian nada")
    func unreadableCover() async throws {
        let store = try makeStore()
        try await imported(store)

        #expect(await !store.replaceCover(bookId: "d", imageData: Data("no es una imagen".utf8)))
        #expect(await store.books()[0].coverVersion == 0)
    }
}
