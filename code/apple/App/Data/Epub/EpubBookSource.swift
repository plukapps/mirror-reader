#if os(iOS)
import Foundation
import ReaderDomain
@preconcurrency import ReadiumShared

/// Abre un libro de la biblioteca para el lector: lo baja si está solo en la nube (LIB-007), lo abre con Readium y
/// junta índice, orden de lectura y última posición (RDR-004, RDR-006).
@MainActor
final class EpubBookSource: ReaderBookSource {
    private let store: LibraryStore
    private let files: LibraryFiles
    private let loader: PublicationLoader
    private let download: DownloadBook

    init(store: LibraryStore, files: LibraryFiles, loader: PublicationLoader, download: DownloadBook) {
        self.store = store
        self.files = files
        self.loader = loader
        self.download = download
    }

    func open(bookId: String) async throws -> OpenedBook {
        try await download(bookId: bookId)
        let publication = try await loader.open(files.bookFile(bookId: bookId))
        let toc = (try? await publication.tableOfContents().get()) ?? []
        let title = await store.title(bookId: bookId) ?? publication.metadata.title ?? ""
        return OpenedBook(
            title: title,
            toc: Self.flatten(toc, depth: 0),
            readingOrder: publication.readingOrder.map { $0.url().string },
            initialLocatorJson: await store.position(bookId: bookId)?.locatorJson,
            publication: OpenedPublication(publication) { publication.close() }
        )
    }

    /// Índice aplanado en el orden en que aparece, con su nivel; sin título, el href (como Android).
    private static func flatten(_ links: [Link], depth: Int) -> [TocItem] {
        links.flatMap { link -> [TocItem] in
            let href = link.url().string
            let title = link.title?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
            let item = TocItem(title: title.isEmpty ? href : title, href: href, depth: depth)
            return [item] + flatten(link.children, depth: depth + 1)
        }
    }
}
#endif
