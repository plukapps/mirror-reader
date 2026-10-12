import Foundation
import ReaderDomain
import Testing
@testable import Reader

private actor FakePositions: ReadingPositionRepository {
    var saved: [String: SavedPosition] = [:]
    func position(bookId: String) async -> SavedPosition? { saved[bookId] }
    func save(bookId: String, position: SavedPosition) async { saved[bookId] = position }
}

private actor FakeEditor: BookEditor {
    var removed: [String] = []
    var coverOk = true
    func replaceCover(bookId: String, imageData: Data) async -> Bool { coverOk }
    func remove(bookId: String) async { removed.append(bookId) }
    func failCovers() { coverOk = false }
}

// Mismos casos que `BookActionsViewModelTest` de Android.
@MainActor
struct BookActionsViewModelTests {
    private let positions = FakePositions()
    private let editor = FakeEditor()
    private let dracula = LibraryBook(id: "d", title: "Drácula", author: "Bram Stoker", coverPath: nil, progressPercent: 42)

    private func makeViewModel(reloads: Reloads = Reloads(), messages: Messages = Messages()) -> BookActionsViewModel {
        BookActionsViewModel(
            actions: BookActions(positions: positions, editor: editor, requestFlush: {}),
            reload: { reloads.count += 1 },
            notify: { messages.list.append($0) }
        )
    }

    @MainActor final class Reloads { var count = 0 }
    @MainActor final class Messages { var list: [String] = [] }

    @Test("LIB-016, LIB-018: sin empezar, terminado; leyendo, las dos; terminado, no leído")
    func menuDependsOnStatus() {
        #expect(BookActionsViewModel.menu(for: dracula) == [.read, .markFinished, .markUnread, .changeCover, .remove])
        #expect(
            BookActionsViewModel.menu(for: LibraryBook(id: "n", title: "N", author: nil, coverPath: nil, progressPercent: nil))
                == [.read, .markFinished, .changeCover, .remove]
        )
        #expect(
            BookActionsViewModel.menu(for: LibraryBook(id: "t", title: "T", author: nil, coverPath: nil, progressPercent: 100))
                == [.read, .markUnread, .changeCover, .remove]
        )
    }

    @Test("LIB-016: la cabecera muestra autor y estado")
    func subtitle() {
        #expect(BookActionsViewModel.subtitle(for: dracula) == "Bram Stoker · 42 %")
        #expect(
            BookActionsViewModel.subtitle(for: LibraryBook(id: "n", title: "N", author: nil, coverPath: nil, progressPercent: nil))
                == "Sin empezar"
        )
    }

    @Test("LIB-018: terminado y no leído guardan, recargan las pantallas y avisan")
    func markFinishedAndUnread() async {
        let reloads = Reloads()
        let messages = Messages()
        let viewModel = makeViewModel(reloads: reloads, messages: messages)

        await viewModel.markFinished(dracula)
        #expect(await positions.saved["d"]?.progress == 1)
        await viewModel.markUnread(dracula)
        #expect(await positions.saved["d"] == SavedPosition(locatorJson: unreadLocator, progress: 0))

        #expect(reloads.count == 2)
        #expect(messages.list == ["Marcado como terminado", "Marcado como no leído"])
    }

    @Test("LIB-020: quitar pide confirmación; cancelar no borra")
    func removeAsksFirst() async {
        let reloads = Reloads()
        let messages = Messages()
        let viewModel = makeViewModel(reloads: reloads, messages: messages)

        viewModel.askRemove(dracula)
        #expect(viewModel.removing == dracula)
        viewModel.cancelRemove()
        #expect(viewModel.removing == nil)
        #expect(await editor.removed.isEmpty)

        viewModel.askRemove(dracula)
        await viewModel.confirmRemove()
        #expect(await editor.removed == ["d"])
        #expect(viewModel.removing == nil)
        #expect(reloads.count == 1)
        #expect(messages.list == ["Se quitó «Drácula» de la biblioteca."])
    }

    @Test("LIB-019: elegir foto abre el selector para ese libro; si no sirve, se avisa")
    func cover() async {
        let reloads = Reloads()
        let messages = Messages()
        let viewModel = makeViewModel(reloads: reloads, messages: messages)

        viewModel.pickCover(for: dracula)
        #expect(viewModel.isPickingCover)
        await viewModel.setCover(Data([1]))
        #expect(reloads.count == 1)
        #expect(messages.list.isEmpty)

        viewModel.pickCover(for: dracula)
        await editor.failCovers()
        await viewModel.setCover(Data([2]))
        #expect(messages.list == ["No se pudo usar esa foto."])
    }
}
