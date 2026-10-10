import SwiftUI

/// Barra superior del lector (RDR-013): volver, título del capítulo y "Aa", sobre el fondo de la página.
struct ReaderTopBar: View {
    let chapterTitle: String
    let settingsOpen: Bool
    let colors: ReaderColors
    let onBack: () -> Void
    let onSettings: () -> Void

    var body: some View {
        HStack(spacing: 8) {
            Button(action: onBack) {
                Image(systemName: "arrow.left")
                    .font(.system(size: 20, weight: .medium))
                    .foregroundStyle(colors.ink)
                    .frame(width: 44, height: 44)
                    .contentShape(Circle())
            }
            .accessibilityLabel("Volver")

            Text(chapterTitle)
                .font(.app(12, .medium))
                .foregroundStyle(colors.muted)
                .lineLimit(1)
                .truncationMode(.tail)
                .frame(maxWidth: .infinity)

            Button(action: onSettings) {
                Text("Aa")
                    .font(.app(17, .bold))
                    .foregroundStyle(settingsOpen ? colors.page : colors.ink)
                    .frame(width: 44, height: 44)
                    .background(settingsOpen ? colors.ink : .clear, in: Circle())
                    .contentShape(Circle())
            }
            .accessibilityLabel("Ajustes de lectura")
            .accessibilityAddTraits(settingsOpen ? .isSelected : [])
        }
        .padding(.horizontal, 10)
        .padding(.top, 4)
        .background(colors.page.ignoresSafeArea(edges: .top))
    }
}

/// Índice del libro (RDR-004): elegir una entrada salta a ese capítulo. Sangría de 16 pt por nivel, como Android.
struct TableOfContentsSheet: View {
    let items: [TocItem]
    let onSelect: (TocItem) -> Void
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            Group {
                if items.isEmpty {
                    Text("Este libro no tiene tabla de contenidos.")
                        .font(.app(15))
                        .foregroundStyle(MarginColors.muted)
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                } else {
                    List(items) { item in
                        Button { onSelect(item) } label: {
                            Text(item.title)
                                .font(.app(16, item.depth == 0 ? .medium : .regular))
                                .foregroundStyle(MarginColors.ink)
                                .padding(.leading, CGFloat(item.depth) * 16)
                                .padding(.vertical, 2)
                        }
                    }
                    .listStyle(.plain)
                }
            }
            .navigationTitle("Índice")
            #if os(iOS)
            .navigationBarTitleDisplayMode(.inline)
            #endif
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cerrar") { dismiss() }.font(.app(16))
                }
            }
        }
        .presentationDetents([.medium, .large])
    }
}
