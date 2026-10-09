import FirebaseFirestore
import Foundation
import ReaderDomain

/// Metadatos en `users/{uid}/books/{bookId}` y posiciones en `users/{uid}/positions/{bookId}` (ver `backend.md`).
/// Por ahora Apple solo lee.
struct FirestoreRemoteLibrary: RemoteLibrary, RemotePositions {
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

    func listPositions() async throws -> [RemotePosition] {
        do {
            let snapshot = try await Firestore.firestore().collection("users/\(try currentUid())/positions")
                .getDocuments(source: .server)
            return snapshot.documents.compactMap { doc in
                guard let locator = doc.get(Field.locatorJson) as? String,
                      let readAt = (doc.get(Field.readAt) as? NSNumber)?.int64Value else { return nil }
                return RemotePosition(
                    bookId: doc.documentID,
                    locatorJson: locator,
                    progress: (doc.get(Field.progress) as? NSNumber)?.doubleValue,
                    readAt: readAt
                )
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
        static let deletedAt = "deletedAt"
        static let locatorJson = "locatorJson"
        static let progress = "progress"
        static let readAt = "readAt"
    }
}
