import FirebaseFirestore
import Foundation
import ReaderDomain

/// Metadatos en `users/{uid}/books/{bookId}` (ver `backend.md`).
struct FirestoreRemoteLibrary: RemoteLibrary {
    func listBooks() async throws -> [RemoteBook] {
        do {
            let snapshot = try await Firestore.firestore().collection("users/\(try currentUid())/books")
                .whereField(Field.deletedAt, isEqualTo: NSNull())
                .getDocuments(source: .server)
            return snapshot.documents.compactMap { doc in
                guard let title = doc.get(Field.title) as? String else { return nil }
                return RemoteBook(
                    id: doc.documentID,
                    title: title,
                    authors: (doc.get(Field.authors) as? [String]) ?? [],
                    sizeBytes: (doc.get(Field.sizeBytes) as? NSNumber)?.int64Value ?? 0,
                    createdAt: (doc.get(Field.createdAt) as? Timestamp)?.dateValue()
                )
            }
        } catch {
            throw remoteError(error)
        }
    }

    /// Crea el documento o, si ya existe, actualiza solo lo editable: la regla rechaza cambios en `filePath`,
    /// `sizeBytes` y `createdAt`. En una transacción, como Android.
    func saveBook(_ book: RemoteBook) async throws {
        let uid = try currentUid()
        let db = Firestore.firestore()
        let ref = db.document("users/\(uid)/books/\(book.id)")
        do {
            _ = try await db.runTransaction { transaction, errorPointer in
                let exists: Bool
                do {
                    exists = try transaction.getDocument(ref).exists
                } catch {
                    errorPointer?.pointee = error as NSError
                    return nil
                }
                if exists {
                    transaction.updateData(
                        [Field.title: book.title, Field.authors: book.authors, Field.updatedAt: FieldValue.serverTimestamp()],
                        forDocument: ref
                    )
                } else {
                    transaction.setData(
                        [
                            Field.title: book.title,
                            Field.authors: book.authors,
                            Field.filePath: "users/\(uid)/books/\(book.id).epub",
                            Field.sizeBytes: book.sizeBytes,
                            Field.createdAt: FieldValue.serverTimestamp(),
                            Field.updatedAt: FieldValue.serverTimestamp(),
                            Field.deletedAt: NSNull(),
                        ],
                        forDocument: ref
                    )
                }
                return nil
            }
        } catch {
            throw remoteError(error)
        }
    }

    private enum Field {
        static let title = "title"
        static let authors = "authors"
        static let sizeBytes = "sizeBytes"
        static let createdAt = "createdAt"
        static let updatedAt = "updatedAt"
        static let filePath = "filePath"
        static let deletedAt = "deletedAt"
    }
}
