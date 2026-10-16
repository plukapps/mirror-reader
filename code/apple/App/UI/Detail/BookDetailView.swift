import ReaderDomain
import SwiftUI

/// LIB-022: detalle del libro, diseño "04 — Book detail", como `BookDetailScreen` de Android. Portada sobre amarillo,
/// título, autor, sus notas (ANN-010) y "Continuar leyendo". Tocar una nota abre el lector en ella; el lector va encima
/// del detalle, así cerrarlo vuelve aquí. Si el libro deja de estar en la biblioteca, se cierra.
struct BookDetailView: View {
    @State var viewModel: BookDetailViewModel
    /// nil en la Mac: no hay lector (ADR 0013).
    let makeReader: ((_ bookId: String, _ noteId: String?) -> ReaderViewModel)?
    let onClose: () -> Void
    /// ANN-010: el lector abierto desde aquí, con la nota o sin ella.
    @State private var reading: ReadingTarget?
    @State private var readerUnavailable = false

    var body: some View {
        Group {
            switch viewModel.phase {
            case .loading:
                MarginColors.paper.ignoresSafeArea()
            case .gone:
                MarginColors.paper.ignoresSafeArea().onAppear(perform: onClose)
            case let .ready(book, notes):
                BookDetailContent(book: book, notes: notes, onClose: onClose, onRead: { open(noteId: nil) }, onOpenNote: { open(noteId: $0.id) })
            }
        }
        .task { await viewModel.load() }
        .alert("El lector llega más adelante a la Mac.", isPresented: $readerUnavailable) {
            Button("Aceptar", role: .cancel) {}
        }
        #if os(iOS)
        .fullScreenCover(item: $reading, onDismiss: { Task { await viewModel.load() } }) { target in
            if let makeReader {
                ReaderView(viewModel: makeReader(viewModel.bookId, target.noteId)) { reading = nil }
            }
        }
        #endif
    }

    private func open(noteId: String?) {
        guard makeReader != nil else {
            readerUnavailable = true
            return
        }
        reading = ReadingTarget(noteId: noteId)
    }
}

private struct ReadingTarget: Identifiable {
    let noteId: String?
    let id = UUID()
}

/// Sin ViewModel, para las vistas previas.
struct BookDetailContent: View {
    let book: LibraryBook
    let notes: [Note]
    let onClose: () -> Void
    let onRead: () -> Void
    let onOpenNote: (Note) -> Void

    var body: some View {
        ZStack(alignment: .bottom) {
            ScrollView {
                VStack(alignment: .leading, spacing: 0) {
                    header
                    VStack(alignment: .leading, spacing: 6) {
                        Text(book.title)
                            .font(.custom("Newsreader", size: 30).weight(.bold))
                            .foregroundStyle(MarginColors.ink)
                            .accessibilityAddTraits(.isHeader)
                            .accessibilityIdentifier("detail-title")
                        if let author = book.author {
                            Text(author).font(.app(14)).foregroundStyle(MarginColors.muted)
                        }
                    }
                    .padding(.horizontal, 24)
                    .padding(.vertical, 20)
                    Rectangle().fill(MarginColors.ink).frame(height: 1.5).padding(.horizontal, 24)
                    Text(notes.count == 1 ? "1 nota" : "\(notes.count) notas")
                        .font(.app(15, .semibold))
                        .foregroundStyle(MarginColors.ink)
                        .padding(.horizontal, 24)
                        .padding(.top, 16)
                        .padding(.bottom, 4)
                        .accessibilityIdentifier("detail-notes-count")
                    if notes.isEmpty {
                        Text("Todavía no hay notas. En el libro, seleccioná un texto y elegí “Nota”.")
                            .font(.app(14))
                            .foregroundStyle(MarginColors.muted)
                            .padding(.horizontal, 24)
                            .padding(.vertical, 8)
                    }
                    ForEach(notes) { note in
                        NoteRow(note: note) { onOpenNote(note) }
                    }
                }
                .padding(.bottom, 104)
            }
            .background(MarginColors.paper)
            ContinueReadingButton(percent: book.progressPercent, onTap: onRead)
                .padding(.horizontal, 20)
                .padding(.bottom, 16)
        }
        .background(MarginColors.paper.ignoresSafeArea())
    }

    private var header: some View {
        ZStack(alignment: .topLeading) {
            MarginColors.yellow.ignoresSafeArea(edges: .top)
            BookCover(book: book)
                .frame(width: 132)
                .frame(maxWidth: .infinity)
                .padding(.top, 48)
                .padding(.bottom, 28)
            Button(action: onClose) {
                Image(systemName: "arrow.left")
                    .font(.system(size: 20, weight: .semibold))
                    .foregroundStyle(MarginColors.onYellow)
                    .frame(width: 48, height: 48)
                    .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .padding(8)
            .accessibilityLabel("Volver")
            .accessibilityIdentifier("detail-back")
        }
    }
}

/// ANN-010: el porcentaje del libro, el texto citado y la nota. Tocarla abre el libro en ese lugar.
private struct NoteRow: View {
    let note: Note
    let onTap: () -> Void

    var body: some View {
        Button(action: onTap) {
            VStack(alignment: .leading, spacing: 6) {
                Text("\(note.percent) %").font(.app(12, .semibold)).foregroundStyle(MarginColors.muted)
                HStack(alignment: .top, spacing: 10) {
                    Rectangle().fill(MarginColors.yellow).frame(width: 3)
                    Text(note.quote)
                        .font(.custom("Newsreader", size: 16).italic())
                        .foregroundStyle(MarginColors.ink)
                        .lineLimit(3)
                        .multilineTextAlignment(.leading)
                }
                .fixedSize(horizontal: false, vertical: true)
                Text(note.text).font(.app(14)).foregroundStyle(MarginColors.ink).multilineTextAlignment(.leading)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.horizontal, 24)
            .padding(.vertical, 14)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityIdentifier("detail-note-\(note.id)")
        Rectangle().fill(MarginColors.line).frame(height: 1).padding(.horizontal, 24)
    }
}

/// "Continuar leyendo" con el progreso (LIB-017, LIB-022), como el botón oscuro del diseño.
private struct ContinueReadingButton: View {
    let percent: Int?
    let onTap: () -> Void

    var body: some View {
        Button(action: onTap) {
            HStack {
                Text(percent == nil ? "Empezar a leer" : "Continuar leyendo")
                    .font(.app(16, .bold))
                    .foregroundStyle(MarginColors.yellow)
                Spacer()
                if let percent {
                    Text("\(percent) %").font(.app(15, .semibold)).foregroundStyle(MarginColors.onInk)
                }
            }
            .padding(.horizontal, 24)
            .frame(height: 56)
            .background(Capsule().fill(MarginColors.inkFill))
        }
        .buttonStyle(.plain)
        .accessibilityIdentifier("detail-continue")
    }
}

#Preview {
    BookDetailContent(
        book: LibraryBook(id: "b", title: "Meditaciones", author: "Marco Aurelio", coverPath: nil, progressPercent: 42),
        notes: [
            Note(id: "1", bookId: "b", locatorJson: "{}", quote: "Pero yo, que he visto la naturaleza del bien…", text: "Para el lunes.", progression: 0.42, createdAt: 1, updatedAt: 1),
            Note(id: "2", bookId: "b", locatorJson: "{}", quote: "El universo es cambio.", text: "Clave.", progression: 0.81, createdAt: 2, updatedAt: 2),
        ],
        onClose: {}, onRead: {}, onOpenNote: { _ in }
    )
}
