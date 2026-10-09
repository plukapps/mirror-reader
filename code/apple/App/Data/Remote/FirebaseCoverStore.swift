import FirebaseStorage
import Foundation
import ReaderDomain

/// Portadas en Storage, `users/{uid}/covers/{bookId}.jpg` (LIB-012).
struct FirebaseCoverStore: CoverStore {
    func downloadCover(bookId: String, to destination: URL) async throws -> Bool {
        let ref = Storage.storage().reference(withPath: "users/\(try currentUid())/covers/\(bookId).jpg")
        do {
            _ = try await ref.writeAsync(toFile: destination)
            return true
        } catch {
            let nsError = error as NSError
            if nsError.domain == StorageErrorDomain, nsError.code == StorageErrorCode.objectNotFound.rawValue { return false }
            throw remoteError(error)
        }
    }
}
