import FirebaseStorage
import Foundation
import ReaderDomain

/// Archivos EPUB en Storage, `users/{uid}/books/{bookId}.epub` (LIB-007, `backend.md`).
struct FirebaseBookStore: BookFileStore {
    func downloadBook(bookId: String, to destination: URL) async throws {
        let ref = Storage.storage().reference(withPath: "users/\(try currentUid())/books/\(bookId).epub")
        do {
            _ = try await ref.writeAsync(toFile: destination)
        } catch {
            throw remoteError(error)
        }
    }
}
