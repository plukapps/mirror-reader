import ReaderDomain
import SwiftUI
import UniformTypeIdentifiers

/// Pantalla de inicio (HOM-001 a HOM-003, HOM-008 a HOM-010), según "02 — Home" del diseño.
/// Tocar un libro abre el lector; "Ver todo" no navega todavía (no hay biblioteca en Apple).
struct HomeView: View {
    let viewModel: HomeViewModel
    @State private var picking = false

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 0) {
                Text(viewModel.greeting.text)
                    .font(.app(44))
                    .tracking(-0.045 * 44)
                    .foregroundStyle(MarginColors.ink)
                    .padding(.horizontal, WindowLayout.horizontalPadding)
                    .padding(.top, 12)
                if !viewModel.loading {
                    let content = viewModel.content
                    if let book = content.continueReading {
                        Button { viewModel.open(bookId: book.id) } label: { ContinueCard(book: book) }
                            .buttonStyle(.plain)
                    } else {
                        NothingReading(libraryEmpty: content.libraryEmpty) {
                            if content.libraryEmpty, viewModel.canImport { picking = true }
                        }
                    }
                    Sections(content: content, onOpen: viewModel.open(bookId:))
                }
            }
            .padding(.bottom, 24)
            .frame(maxWidth: WindowLayout.contentMaxWidth, alignment: .leading)
            .frame(maxWidth: .infinity)
        }
        .background(MarginColors.paper)
        .task { await viewModel.load() }
        // LIB-001: uno o varios EPUB desde Archivos.
        .fileImporter(isPresented: $picking, allowedContentTypes: [.epub], allowsMultipleSelection: true) { result in
            if case let .success(urls) = result { Task { await viewModel.importBooks(urls) } }
        }
    }
}

private extension Greeting {
    var text: String {
        switch self {
        case .morning: "Buenos días."
        case .afternoon: "Buenas tardes."
        case .night: "Buenas noches."
        }
    }
}

private struct ContinueCard: View {
    let book: LibraryBook

    var body: some View {
        let percent = book.progressPercent ?? 0
        HStack(alignment: .top, spacing: 16) {
            BookCover(book: book).frame(width: 96)
            VStack(alignment: .leading, spacing: 0) {
                Text("Continuar leyendo")
                    .font(.app(12, .bold))
                    .foregroundStyle(MarginColors.yellow)
                Text(book.title)
                    .font(.app(20, .heavy))
                    .foregroundStyle(.white)
                    .lineLimit(2)
                    .padding(.top, 4)
                if let author = book.author {
                    Text(author).font(.app(13)).foregroundStyle(MarginColors.line).lineLimit(1)
                }
                Spacer(minLength: 12)
                VStack(alignment: .leading, spacing: 6) {
                    ProgressBar(percent: percent, fill: MarginColors.yellow, track: .white.opacity(0.2))
                    Text("\(percent) %").font(.app(12, .semibold)).foregroundStyle(.white)
                }
            }
            .frame(minHeight: 144)
        }
        .padding(16)
        .background(MarginColors.ink, in: RoundedRectangle(cornerRadius: 20))
        .padding(.horizontal, WindowLayout.horizontalPadding)
        .padding(.top, 24)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel([String(localized: "Continuar leyendo"), book.title, book.author, "\(percent) %"].compactMap { $0 }.joined(separator: ". "))
    }
}

private struct NothingReading: View {
    let libraryEmpty: Bool
    let onAction: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Nada en lectura todavía").font(.app(20, .semibold)).foregroundStyle(MarginColors.ink)
            Text("Abrí un libro de tu biblioteca y lo retomás desde acá.").font(.app(14)).foregroundStyle(MarginColors.muted)
            Button(action: onAction) {
                Text(libraryEmpty ? "Importar un EPUB" : "Ir a la biblioteca")
                    .font(.app(14, .bold))
                    .foregroundStyle(MarginColors.yellow)
                    .padding(.horizontal, WindowLayout.horizontalPadding)
                    .frame(height: 44)
                    .background(MarginColors.ink, in: Capsule())
            }
            .buttonStyle(.plain)
            .padding(.top, 8)
        }
        .padding(20)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(.white, in: RoundedRectangle(cornerRadius: 20))
        .padding(.horizontal, WindowLayout.horizontalPadding)
        .padding(.top, 24)
    }
}

private struct Sections: View {
    let content: HomeContent
    let onOpen: (String) -> Void

    var body: some View {
        if !content.reading.isEmpty {
            BookRow(title: "Leyendo", count: content.readingCount, books: content.reading, onOpen: onOpen) { book in
                let percent = book.progressPercent ?? 0
                TitleAndAuthor(book: book, subtitle: book.author)
                HStack(spacing: 8) {
                    ProgressBar(percent: percent, fill: MarginColors.ink, track: MarginColors.line)
                    Text("\(percent)%").font(.app(11, .medium)).foregroundStyle(MarginColors.muted)
                }
                .padding(.top, 2)
            }
        }
        if !content.recentlyAdded.isEmpty {
            BookRow(title: "Agregados recientemente", count: content.recentlyAddedCount, books: content.recentlyAdded, onOpen: onOpen) { book in
                TitleAndAuthor(book: book, subtitle: book.author)
            }
        }
        if !content.finished.isEmpty {
            BookRow(title: "Terminados", count: content.finishedCount, books: content.finished, finished: true, onOpen: onOpen) { book in
                TitleAndAuthor(book: book, subtitle: finishedSubtitle(author: book.author, lastReadAt: book.lastReadAt))
            }
        }
    }
}

/// Fila con título, total y "Ver todo" (HOM-008 a HOM-011).
private struct BookRow<Details: View>: View {
    let title: LocalizedStringKey
    let count: Int
    let books: [LibraryBook]
    var finished = false
    let onOpen: (String) -> Void
    @ViewBuilder let details: (LibraryBook) -> Details

    var body: some View {
        HStack(alignment: .center) {
            HStack(alignment: .lastTextBaseline, spacing: 8) {
                Text(title).font(.app(18, .bold)).tracking(-0.02 * 18).foregroundStyle(MarginColors.ink)
                Text("\(count)").font(.app(13, .medium)).foregroundStyle(MarginColors.muted)
            }
            Spacer()
            Text("Ver todo").font(.app(13, .semibold)).underline().foregroundStyle(MarginColors.ink)
        }
        .padding(.horizontal, WindowLayout.horizontalPadding)
        .padding(.top, 26)
        .padding(.bottom, 12)

        ScrollView(.horizontal, showsIndicators: false) {
            HStack(alignment: .top, spacing: 12) {
                ForEach(books) { book in
                    Button { onOpen(book.id) } label: {
                        VStack(alignment: .leading, spacing: 4) {
                            BookCover(book: book)
                                .overlay(alignment: .topTrailing) { if finished { FinishedBadge() } }
                            VStack(alignment: .leading, spacing: 1) { details(book) }
                        }
                        .frame(width: 104)
                        .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                    .accessibilityElement(children: .combine)
                }
            }
            .padding(.horizontal, WindowLayout.horizontalPadding)
        }
    }
}

private struct TitleAndAuthor: View {
    let book: LibraryBook
    let subtitle: String?

    var body: some View {
        Text(book.title).font(.app(14, .semibold)).foregroundStyle(MarginColors.ink).lineLimit(1)
        if let subtitle {
            Text(subtitle).font(.app(12)).foregroundStyle(MarginColors.muted).lineLimit(1)
        }
    }
}

private struct FinishedBadge: View {
    var body: some View {
        Image(systemName: "checkmark")
            .font(.system(size: 10, weight: .heavy))
            .foregroundStyle(MarginColors.ink)
            .frame(width: 22, height: 22)
            .background(MarginColors.yellow, in: Circle())
            .padding(6)
            .accessibilityLabel("Terminado")
    }
}

private struct ProgressBar: View {
    let percent: Int
    let fill: Color
    let track: Color

    var body: some View {
        GeometryReader { geometry in
            ZStack(alignment: .leading) {
                Capsule().fill(track)
                Capsule().fill(fill).frame(width: geometry.size.width * CGFloat(min(max(percent, 0), 100)) / 100)
            }
        }
        .frame(height: 4)
        .accessibilityHidden(true)
    }
}

#Preview {
    HomeView(viewModel: HomeViewModel(library: FakeLibraryRepository()))
        .frame(width: 720, height: 900)
}
