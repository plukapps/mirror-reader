import ReaderDomain
import SwiftUI

/// Biblioteca del usuario (LIB-007, LIB-010, LIB-011), según "05 — Library" del diseño y `LibraryScreen` de Android.
/// Tocar un libro lo abre en el lector (RDR-007; en la Mac avisa que todavía no hay).
struct LibraryView: View {
    let viewModel: LibraryViewModel
    /// nil: sin botón "Importar" (la Mac no tiene dónde mostrar el aviso hasta K-078).
    var onImport: (() -> Void)?
    var onOpen: (String) -> Void = { _ in }

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            Header(onImport: onImport)
            Tabs(viewModel: viewModel)
            if viewModel.isLibraryEmpty {
                EmptyLibrary(onImport: onImport)
            } else if !viewModel.loading {
                BookGrid(books: viewModel.books, onOpen: onOpen)
            }
            Spacer(minLength: 0)
        }
        .frame(maxWidth: WindowLayout.contentMaxWidth)
        .frame(maxWidth: .infinity)
        .background(MarginColors.paper)
        .task { await viewModel.load() }
    }
}

private struct Header: View {
    let onImport: (() -> Void)?

    var body: some View {
        HStack {
            Text("Biblioteca")
                .font(.app(44))
                .tracking(-0.045 * 44)
                .foregroundStyle(MarginColors.ink)
                .accessibilityAddTraits(.isHeader)
            Spacer()
            if let onImport {
                Button(action: onImport) {
                    HStack(spacing: 4) {
                        Image(systemName: "plus").font(.system(size: 17, weight: .semibold))
                        Text("Importar").font(.app(13, .bold))
                    }
                    .foregroundStyle(MarginColors.ink)
                    .padding(.leading, 10)
                    .padding(.trailing, 14)
                    .frame(height: 40)
                    .background(MarginColors.yellow, in: Capsule())
                }
                .buttonStyle(.plain)
            }
        }
        .padding(.horizontal, WindowLayout.horizontalPadding)
        .padding(.top, 12)
    }
}

private struct Tabs: View {
    let viewModel: LibraryViewModel

    var body: some View {
        HStack(spacing: 16) {
            ForEach(LibraryFilter.allCases, id: \.self) { filter in
                Tab(label: filter.label, count: viewModel.count(filter), selected: viewModel.filter == filter) {
                    viewModel.select(filter)
                }
            }
        }
        .padding(.horizontal, WindowLayout.horizontalPadding)
        .padding(.top, 18)
        // La barra de la pestaña activa se superpone a la línea inferior, como en el diseño.
        .background(alignment: .bottom) { MarginColors.line.frame(height: 1) }
    }
}

private struct Tab: View {
    let label: String
    let count: Int
    let selected: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            VStack(spacing: 10) {
                Text("\(label) \(count)")
                    .font(.app(14, .semibold))
                    .foregroundStyle(selected ? MarginColors.ink : MarginColors.muted)
                    .lineLimit(1)
                (selected ? MarginColors.ink : .clear).frame(height: 3)
            }
            .frame(maxWidth: .infinity)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(selected ? [.isSelected] : [])
    }
}

private extension LibraryFilter {
    var label: String {
        switch self {
        case .all: String(localized: "Todos")
        case .reading: String(localized: "Leyendo")
        case .finished: String(localized: "Terminados")
        }
    }
}

private struct BookGrid: View {
    let books: [LibraryBook]
    let onOpen: (String) -> Void

    var body: some View {
        ScrollView {
            LazyVGrid(columns: [GridItem(.adaptive(minimum: 96), spacing: 12, alignment: .top)], spacing: 16) {
                ForEach(books) { book in
                    Button { onOpen(book.id) } label: { BookCell(book: book).contentShape(Rectangle()) }
                        .buttonStyle(.plain)
                }
            }
            .padding(.horizontal, WindowLayout.horizontalPadding)
            .padding(.top, 18)
            .padding(.bottom, 24)
        }
    }
}

private struct BookCell: View {
    let book: LibraryBook

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            BookCover(book: book)
            // LIB-007: un libro solo en la nube se distingue de los descargados.
            if !book.isDownloaded {
                Text("En la nube").font(.app(11, .semibold)).foregroundStyle(MarginColors.muted)
            }
            switch book.status {
            case .reading:
                ProgressLine(percent: book.progressPercent ?? 0)
                Text(statusText).font(.app(11, .medium)).foregroundStyle(MarginColors.muted)
            case .finished:
                HStack(spacing: 4) {
                    Image(systemName: "checkmark").font(.system(size: 11, weight: .bold))
                    Text(statusText).font(.app(11, .semibold))
                }
                .foregroundStyle(MarginColors.ink)
            case .new:
                Text(statusText).font(.app(11, .medium)).foregroundStyle(MarginColors.muted)
            }
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(accessibilityText)
    }

    private var statusText: String {
        switch book.status {
        case .new: String(localized: "Nuevo")
        case .finished: String(localized: "Terminado")
        case .reading: "\(book.progressPercent ?? 0) %"
        }
    }

    private var accessibilityText: String {
        let cloud = book.isDownloaded ? nil : String(localized: "En la nube")
        return [book.title, book.author, statusText, cloud].compactMap { $0 }.joined(separator: ". ")
    }
}

private struct ProgressLine: View {
    let percent: Int

    var body: some View {
        GeometryReader { geometry in
            ZStack(alignment: .leading) {
                Capsule().fill(MarginColors.line)
                Capsule().fill(MarginColors.ink).frame(width: geometry.size.width * CGFloat(min(max(percent, 0), 100)) / 100)
            }
        }
        .frame(height: 3)
    }
}

private struct EmptyLibrary: View {
    let onImport: (() -> Void)?

    var body: some View {
        VStack(spacing: 0) {
            Text("Tu biblioteca está vacía").font(.app(22, .semibold)).foregroundStyle(MarginColors.ink)
            Text("Importá tus EPUB para empezar a leer.")
                .font(.app(14))
                .foregroundStyle(MarginColors.muted)
                .multilineTextAlignment(.center)
                .padding(.top, 8)
            if let onImport {
                Button(action: onImport) {
                    Text("Importar un EPUB")
                        .font(.app(14, .bold))
                        .foregroundStyle(MarginColors.yellow)
                        .padding(.horizontal, 20)
                        .frame(height: 44)
                        .background(MarginColors.ink, in: Capsule())
                }
                .buttonStyle(.plain)
                .padding(.top, 20)
            }
        }
        .padding(32)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}

#Preview {
    let viewModel = LibraryViewModel(library: FakeLibraryRepository())
    LibraryView(viewModel: viewModel, onImport: {})
        .frame(width: 390, height: 844)
}
