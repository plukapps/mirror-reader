import ReaderDomain
import SwiftUI

/// Búsqueda local en la biblioteca (LIB-006, LIB-013 a LIB-015), según "03 — Search" del diseño y
/// `SearchScreen` de Android. Tocar un resultado abre el libro en el lector (RDR-007).
struct SearchView: View {
    @Bindable var viewModel: SearchViewModel
    var onOpen: (String) -> Void = { _ in }
    @FocusState private var fieldFocused: Bool

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            Text("Buscar")
                .font(.app(44))
                .tracking(-0.045 * 44)
                .foregroundStyle(MarginColors.ink)
                .padding(.horizontal, WindowLayout.horizontalPadding)
                .padding(.top, 12)
            SearchField(query: $viewModel.query, focused: $fieldFocused, onClear: viewModel.clear)
            ScopeChips(selected: $viewModel.scope)
            if !viewModel.loading {
                if !viewModel.hasQuery {
                    Message(text: String(localized: "Buscá en tu biblioteca por título o autor."))
                } else if viewModel.results.isEmpty {
                    let query = viewModel.query.trimmingCharacters(in: .whitespaces)
                    Message(text: String(localized: "Sin resultados para «\(query)»."))
                } else {
                    Results(books: viewModel.results, onOpen: onOpen)
                }
            }
            Spacer(minLength: 0)
        }
        .frame(maxWidth: WindowLayout.contentMaxWidth, alignment: .leading)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(MarginColors.paper)
        .task {
            // El campo toma el foco solo la primera vez, no cada vez que se vuelve a la pestaña.
            if !viewModel.autoFocused {
                viewModel.autoFocused = true
                fieldFocused = true
            }
            await viewModel.load()
        }
    }
}

private struct SearchField: View {
    @Binding var query: String
    var focused: FocusState<Bool>.Binding
    let onClear: () -> Void

    var body: some View {
        let shape = RoundedRectangle(cornerRadius: 28)
        HStack(spacing: 10) {
            Image("NavSearch")
                .resizable()
                .frame(width: 22, height: 22)
                .foregroundStyle(MarginColors.ink)
                .accessibilityHidden(true)
            TextField("Títulos y autores", text: $query, prompt: Text("Títulos y autores").foregroundStyle(MarginColors.muted))
                .font(.app(16, .medium))
                .foregroundStyle(MarginColors.ink)
                .tint(MarginColors.ink)
                .textFieldStyle(.plain)
                .autocorrectionDisabled()
                #if os(iOS)
                .textInputAutocapitalization(.never)
                #endif
                .submitLabel(.search)
                .onSubmit { focused.wrappedValue = false }
                .focused(focused)
            if !query.isEmpty {
                Button(action: onClear) {
                    Image("SearchClose")
                        .resizable()
                        .frame(width: 20, height: 20)
                        .foregroundStyle(MarginColors.muted)
                        .padding(2)
                        .contentShape(Circle())
                }
                .buttonStyle(.plain)
                .accessibilityLabel(Text("Borrar búsqueda"))
            }
        }
        .padding(.horizontal, 18)
        .frame(height: 56)
        .background(MarginColors.field, in: shape)
        .overlay(shape.strokeBorder(MarginColors.ink, lineWidth: 2))
        .padding(.horizontal, 16)
        .padding(.top, 16)
    }
}

/// LIB-015: "Todo" y "Autores". Los chips "En mi biblioteca" y "Gratis" del diseño son del catálogo.
private struct ScopeChips: View {
    @Binding var selected: SearchScope

    var body: some View {
        HStack(spacing: 8) {
            ForEach(SearchScope.allCases, id: \.self) { scope in
                let isSelected = scope == selected
                Button { selected = scope } label: {
                    Text(scope.label)
                        .font(.app(13, .semibold))
                        .foregroundStyle(isSelected ? MarginColors.yellow : MarginColors.ink)
                        .padding(.horizontal, 14)
                        .frame(height: 34)
                        .background(isSelected ? MarginColors.ink : .clear, in: Capsule())
                        .overlay {
                            if !isSelected { Capsule().strokeBorder(MarginColors.ink, lineWidth: 1.5) }
                        }
                        .contentShape(Capsule())
                }
                .buttonStyle(.plain)
                .accessibilityAddTraits(isSelected ? .isSelected : [])
            }
        }
        .padding(.horizontal, 16)
        .padding(.top, 14)
    }
}

private extension SearchScope {
    var label: LocalizedStringKey {
        switch self {
        case .all: "Todo"
        case .authors: "Autores"
        }
    }
}

private struct Message: View {
    let text: String

    var body: some View {
        Text(text)
            .font(.app(14))
            .foregroundStyle(MarginColors.muted)
            .padding(.horizontal, WindowLayout.horizontalPadding)
            .padding(.top, 24)
    }
}

private struct Results: View {
    let books: [LibraryBook]
    let onOpen: (String) -> Void

    var body: some View {
        ScrollView {
            LazyVStack(alignment: .leading, spacing: 0) {
                Text(searchResultsCount(books.count).uppercased())
                    .font(.app(11, .semibold))
                    .tracking(0.08 * 11)
                    .foregroundStyle(MarginColors.muted)
                    .padding(.top, 22)
                    .padding(.bottom, 6)
                ForEach(Array(books.enumerated()), id: \.element.id) { index, book in
                    Button { onOpen(book.id) } label: {
                        ResultRow(book: book, showDivider: index < books.count - 1).contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                }
            }
            .padding(.horizontal, WindowLayout.horizontalPadding)
            .padding(.bottom, 24)
        }
        .scrollDismissesKeyboard(.immediately)
    }
}

/// LIB-014: portada, título, autor y estado. VoiceOver lo lee junto.
private struct ResultRow: View {
    let book: LibraryBook
    let showDivider: Bool

    var body: some View {
        let status = searchStatus(book)
        let reading = book.status == .reading
        VStack(spacing: 0) {
            HStack(spacing: 14) {
                BookCover(book: book).frame(width: 52)
                VStack(alignment: .leading, spacing: 3) {
                    Text(book.title)
                        .font(.app(16, .bold))
                        .foregroundStyle(MarginColors.ink)
                        .lineLimit(2)
                    if let author = book.author {
                        Text(author).font(.app(13)).foregroundStyle(MarginColors.muted).lineLimit(1)
                    }
                    HStack(spacing: 6) {
                        if reading { Circle().fill(MarginColors.readingDot).frame(width: 6, height: 6) }
                        Text(status)
                            .font(.app(11, .semibold))
                            .foregroundStyle(reading ? MarginColors.ink : MarginColors.muted)
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                Image("SearchChevron")
                    .resizable()
                    .frame(width: 22, height: 22)
                    .foregroundStyle(MarginColors.ink)
            }
            .padding(.vertical, 12)
            if showDivider { MarginColors.line.frame(height: 1) }
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel([book.title, book.author, status].compactMap { $0 }.joined(separator: ". "))
    }
}

#Preview {
    let viewModel = SearchViewModel(library: FakeLibraryRepository())
    viewModel.query = "an"
    return SearchView(viewModel: viewModel)
}
