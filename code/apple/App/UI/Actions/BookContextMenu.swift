import ReaderDomain
import SwiftUI

extension View {
    /// Menú de un libro con mantener presionado (en la Mac, clic secundario): LIB-016. `onRead` abre el libro como
    /// tocarlo (LIB-017). Sin `BookActionsViewModel` en el entorno (vistas previas) no hace nada.
    func bookContextMenu(_ book: LibraryBook, onRead: @escaping () -> Void) -> some View {
        modifier(BookContextMenu(book: book, onRead: onRead))
    }
}

private struct BookContextMenu: ViewModifier {
    let book: LibraryBook
    let onRead: () -> Void
    @Environment(BookActionsViewModel.self) private var actions: BookActionsViewModel?

    func body(content: Content) -> some View {
        if let actions {
            #if os(iOS)
            // El sistema levanta la vista previa y desenfoca el fondo, como el diseño 05b.
            content.contextMenu { items(actions) } preview: { BookMenuPreview(book: book) }
            #else
            content.contextMenu { items(actions) }
            #endif
        } else {
            content
        }
    }

    @ViewBuilder
    private func items(_ actions: BookActionsViewModel) -> some View {
        #if os(macOS)
        Section("\(book.title) · \(BookActionsViewModel.subtitle(for: book))") {}
        #endif
        ForEach(BookActionsViewModel.menu(for: book), id: \.self) { action in
            switch action {
            case .read:
                Button(action: onRead) { Label("Leer libro", systemImage: "book.fill") }
            case .markFinished:
                Button { Task { await actions.markFinished(book) } } label: {
                    Label("Marcar como terminado", systemImage: "checkmark.circle")
                }
            case .markUnread:
                Button { Task { await actions.markUnread(book) } } label: {
                    Label("Marcar como no leído", systemImage: "arrow.uturn.backward.circle")
                }
            case .changeCover:
                Button { actions.pickCover(for: book) } label: { Label("Subir foto de portada", systemImage: "photo.badge.plus") }
                Divider()
            case .remove:
                Button(role: .destructive) { actions.askRemove(book) } label: {
                    Label("Quitar de la biblioteca", systemImage: "trash")
                }
            }
        }
    }
}

/// Lo que se levanta al mantener presionado: la portada con el título y "Autor · estado" (diseño 05b).
private struct BookMenuPreview: View {
    let book: LibraryBook

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            BookCover(book: book).frame(width: 200)
            VStack(alignment: .leading, spacing: 2) {
                Text(book.title).font(.app(15, .bold)).foregroundStyle(MarginColors.ink).lineLimit(2)
                Text(BookActionsViewModel.subtitle(for: book)).font(.app(12)).foregroundStyle(MarginColors.muted).lineLimit(1)
            }
            .frame(width: 200, alignment: .leading)
        }
        .padding(16)
        .background(MarginColors.field)
    }
}
