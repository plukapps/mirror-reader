import FirebaseStorage
import Foundation
import ReaderDomain

/// Archivos EPUB en Storage, `users/{uid}/books/{bookId}.epub` (LIB-007, `backend.md`).
struct FirebaseBookStore: BookFileStore {
    func downloadBook(bookId: String, to destination: URL) async throws {
        let ref = try bookRef(bookId)
        do {
            _ = try await ref.writeAsync(toFile: destination)
        } catch {
            throw remoteError(error)
        }
    }

    /// Un libro ya subido no se vuelve a subir: el nombre es su hash y las reglas no permiten sobrescribir (LIB-002).
    func uploadBook(bookId: String, from file: URL) async throws {
        try await uploadIfMissing(try bookRef(bookId), file: file, contentType: "application/epub+zip")
    }

    private func bookRef(_ bookId: String) throws -> StorageReference {
        Storage.storage().reference(withPath: "users/\(try currentUid())/books/\(bookId).epub")
    }
}

/// Sube `file` a `ref` salvo que ya exista, como `FirebaseBookFileStore` de Android.
func uploadIfMissing(_ ref: StorageReference, file: URL, contentType: String) async throws {
    do {
        _ = try await ref.getMetadata()
        return
    } catch {
        guard isObjectNotFound(error) else { throw remoteError(error) }
    }
    let metadata = StorageMetadata()
    metadata.contentType = contentType
    do {
        _ = try await ref.putFileAsync(from: file, metadata: metadata)
    } catch {
        throw uploadError(error)
    }
}

func isObjectNotFound(_ error: Error) -> Bool {
    let nsError = error as NSError
    return nsError.domain == StorageErrorDomain && nsError.code == StorageErrorCode.objectNotFound.rawValue
}
