#if os(iOS)
import Foundation
import ReaderDomain

/// Instala los libros bajados de la nube y, si todavía no tienen portada, la saca del EPUB (LIB-012), como el
/// `install` de Android. Así la portada no depende de que la nube ya la tenga: el dispositivo que importó el libro
/// sube la portada después del libro y otro puede bajarlo en el medio (SYN-015).
struct CoverExtractingBookFiles: BookFiles {
    let store: LibraryStore
    let importer: EpubImporter

    func isDownloaded(bookId: String) async -> Bool { await store.isDownloaded(bookId: bookId) }

    func newTempFile() -> URL { store.newTempFile() }

    func installBook(bookId: String, from file: URL) async throws {
        try await store.installBook(bookId: bookId, from: file)
        await importer.extractCoverIfMissing(bookId: bookId)
    }
}
#endif
