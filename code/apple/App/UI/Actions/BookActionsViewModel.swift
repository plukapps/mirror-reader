import Foundation
import Observation
import ReaderDomain

/// Opciones del menú de un libro, en el orden del diseño 05b (LIB-016). Como `BookAction` de Android.
enum BookAction: Equatable {
    case read, markFinished, markUnread, changeCover, remove
}

/// Menú de un libro con mantener presionado (LIB-016 a LIB-020), compartido por Inicio, Estantes y Buscar.
/// "Leer libro" lo resuelve cada pantalla; el resto pasa por aquí y después se recargan las pantallas.
@MainActor
@Observable
final class BookActionsViewModel {
    /// Libro que espera la confirmación de "Quitar de la biblioteca" (LIB-020).
    private(set) var removing: LibraryBook?
    /// El selector de fotos está abierto para `coverBookId` (LIB-019).
    var isPickingCover = false
    private(set) var coverBookId: String?

    private let actions: BookActions
    private let reload: () async -> Void
    private let notify: (String) -> Void

    init(actions: BookActions, reload: @escaping () async -> Void, notify: @escaping (String) -> Void) {
        self.actions = actions
        self.reload = reload
        self.notify = notify
    }

    /// Sin empezar: terminado. Leyendo: terminado y no leído. Terminado: no leído (LIB-018).
    static func menu(for book: LibraryBook) -> [BookAction] {
        var actions: [BookAction] = [.read]
        if book.status != .finished { actions.append(.markFinished) }
        if book.status != .new { actions.append(.markUnread) }
        return actions + [.changeCover, .remove]
    }

    /// "Autor · estado" de la cabecera del menú.
    static func subtitle(for book: LibraryBook) -> String {
        let status = switch book.status {
        case .new: String(localized: "Sin empezar")
        case .finished: String(localized: "Terminado")
        case .reading: "\(book.progressPercent ?? 0) %"
        }
        return [book.author, status].compactMap { $0 }.joined(separator: " · ")
    }

    func markFinished(_ book: LibraryBook) async {
        await actions.markFinished(bookId: book.id)
        await reload()
        notify(String(localized: "Marcado como terminado"))
    }

    func markUnread(_ book: LibraryBook) async {
        await actions.markUnread(bookId: book.id)
        await reload()
        notify(String(localized: "Marcado como no leído"))
    }

    func askRemove(_ book: LibraryBook) {
        removing = book
    }

    func cancelRemove() {
        removing = nil
    }

    /// Recibe el libro en lugar de leer `removing`: al tocar el botón, SwiftUI cierra la alerta (y la limpia) antes de
    /// que corra esta tarea.
    func confirmRemove(_ book: LibraryBook) async {
        removing = nil
        await actions.remove(bookId: book.id)
        await reload()
        notify(String(localized: "Se quitó «\(book.title)» de la biblioteca."))
    }

    func pickCover(for book: LibraryBook) {
        coverBookId = book.id
        isPickingCover = true
    }

    /// La foto elegida para el libro de `pickCover(for:)`.
    func setCover(_ data: Data) async {
        guard let bookId = coverBookId else { return }
        coverBookId = nil
        if await actions.setCover(bookId: bookId, imageData: data) {
            await reload()
        } else {
            notify(String(localized: "No se pudo usar esa foto."))
        }
    }

    /// La foto elegida no se pudo leer.
    func coverFailed() {
        guard coverBookId != nil else { return }
        coverBookId = nil
        notify(String(localized: "No se pudo usar esa foto."))
    }
}
