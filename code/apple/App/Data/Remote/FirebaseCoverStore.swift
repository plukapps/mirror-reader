import FirebaseStorage
import Foundation
import ReaderDomain

/// Portadas en Storage, `users/{uid}/covers/{bookId}.jpg` (LIB-012). No cuentan para la cuota (ADR 0008).
struct FirebaseCoverStore: CoverStore {
    func downloadCover(bookId: String, to destination: URL) async throws -> Bool {
        let ref = try coverRef(bookId)
        do {
            _ = try await ref.writeAsync(toFile: destination)
            return true
        } catch {
            if isObjectNotFound(error) { return false }
            throw remoteError(error)
        }
    }

    func uploadCover(bookId: String, from file: URL) async throws {
        try await uploadIfMissing(try coverRef(bookId), file: file, contentType: "image/jpeg")
    }

    private func coverRef(_ bookId: String) throws -> StorageReference {
        Storage.storage().reference(withPath: "users/\(try currentUid())/covers/\(bookId).jpg")
    }
}
