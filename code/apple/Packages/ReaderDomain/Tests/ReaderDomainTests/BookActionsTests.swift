import Foundation
import Testing
@testable import ReaderDomain

// Mismos casos que `BookActionsTest` de Android: el spec es el contrato común (ADR 0001).
struct BookActionsTests {
    actor FakePositions: ReadingPositionRepository {
        var saved: [String: SavedPosition] = [:]
        func position(bookId: String) async -> SavedPosition? { saved[bookId] }
        func save(bookId: String, position: SavedPosition) async { saved[bookId] = position }
        func set(_ bookId: String, _ position: SavedPosition) { saved[bookId] = position }
    }

    actor FakeEditor: BookEditor {
        var removed: [String] = []
        var covers: [String] = []
        var coverOk = true
        func replaceCover(bookId: String, imageData: Data) async -> Bool {
            covers.append(bookId)
            return coverOk
        }
        func remove(bookId: String) async { removed.append(bookId) }
        func failCovers() { coverOk = false }
    }

    actor Flushes {
        var count = 0
        var pushes = 0
        func add() { count += 1 }
        func addPush() { pushes += 1 }
    }

    let positions = FakePositions()
    let editor = FakeEditor()
    let flushes = Flushes()
    var actions: BookActions {
        let flushes = flushes
        return BookActions(positions: positions, editor: editor, requestFlush: { await flushes.add() }, requestRemovalPush: { await flushes.addPush() })
    }

    @Test("LIB-018: terminado conserva la última posición y queda al 100 %")
    func markFinishedKeepsLocator() async {
        await positions.set("b", SavedPosition(locatorJson: #"{"href":"c3.xhtml"}"#, progress: 0.42))
        await actions.markFinished(bookId: "b")
        #expect(await positions.saved["b"] == SavedPosition(locatorJson: #"{"href":"c3.xhtml"}"#, progress: 1))
        #expect(await flushes.count == 1)
    }

    @Test("LIB-018: un libro nunca abierto se marca terminado sin posición real")
    func markFinishedWithoutPosition() async {
        await actions.markFinished(bookId: "b")
        #expect(await positions.saved["b"] == SavedPosition(locatorJson: unreadLocator, progress: 1))
    }

    @Test("LIB-018: no leído vuelve al principio y viaja como posición")
    func markUnread() async {
        await positions.set("b", SavedPosition(locatorJson: #"{"href":"c9.xhtml"}"#, progress: 1))
        await actions.markUnread(bookId: "b")
        #expect(await positions.saved["b"] == SavedPosition(locatorJson: unreadLocator, progress: 0))
        #expect(await flushes.count == 1)
    }

    @Test("LIB-019: la portada se delega y se informa el resultado")
    func setCover() async {
        #expect(await actions.setCover(bookId: "b", imageData: Data([1])))
        await editor.failCovers()
        #expect(await !actions.setCover(bookId: "b", imageData: Data([2])))
    }

    @Test("LIB-020: quitar se delega y no envía posiciones")
    func remove() async {
        await actions.remove(bookId: "b")
        #expect(await editor.removed == ["b"])
        #expect(await flushes.count == 0)
        #expect(await flushes.pushes == 1)
    }

    @Test("LIB-018: una posición «no leído» cuenta como sin abrir, salvo que esté terminada")
    func unreadPositionCountsAsNew() {
        #expect(positionProgression(locatorJson: unreadLocator, progress: 0) == nil)
        #expect(positionProgression(locatorJson: unreadLocator, progress: nil) == nil)
        #expect(positionProgression(locatorJson: unreadLocator, progress: 1) == 1)
        // "{}" es una posición real que el lector no pudo serializar: cuenta su progreso.
        #expect(positionProgression(locatorJson: "{}", progress: 0.42) == 0.42)
        #expect(positionProgression(locatorJson: #"{"href":"c3.xhtml"}"#, progress: 0.42) == 0.42)
        // Sin progresión guardada no hay porcentaje (como hasta ahora en Apple).
        #expect(positionProgression(locatorJson: #"{"href":"c1.xhtml"}"#, progress: nil) == nil)
    }
}
