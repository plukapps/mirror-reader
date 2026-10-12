import FirebaseFirestore
import Foundation
import ReaderDomain

/// Metadatos en `users/{uid}/books/{bookId}` (ver `backend.md`).
struct FirestoreRemoteLibrary: RemoteLibrary, RemoteBookChanges {
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
                    // Un libro borrado que se vuelve a importar revive (LIB-020).
                    transaction.updateData(
                        [
                            Field.title: book.title, Field.authors: book.authors, Field.updatedAt: FieldValue.serverTimestamp(),
                            Field.deletedAt: NSNull(),
                        ],
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

    /// SYN-014: una sola desigualdad sobre `updatedAt`, sin índice compuesto. `deletedAt` se filtra aquí.
    func observeAlive(since: Int64) -> AsyncThrowingStream<[String], any Error> {
        AsyncThrowingStream { continuation in
            let uid: String
            do {
                uid = try currentUid()
            } catch {
                continuation.finish(throwing: error)
                return
            }
            let registration = Firestore.firestore().collection("users/\(uid)/books")
                .whereField(Field.updatedAt, isGreaterThan: Timestamp(date: Date(timeIntervalSince1970: Double(since) / 1000)))
                .addSnapshotListener { snapshot, error in
                    guard let snapshot else {
                        continuation.finish(throwing: error.map(remoteError) ?? RemoteUnavailableError("Sin datos de la nube."))
                        return
                    }
                    let alive = snapshot.documents.filter { $0.get(Field.deletedAt) == nil || $0.get(Field.deletedAt) is NSNull }
                    continuation.yield(alive.map(\.documentID))
                }
            let listener = ListenerBox(registration)
            continuation.onTermination = { _ in listener.remove() }
        }
    }

    /// El registro del SDK no se declara `Sendable`, pero `remove()` se puede llamar desde cualquier hilo.
    private final class ListenerBox: @unchecked Sendable {
        private let registration: any ListenerRegistration
        init(_ registration: any ListenerRegistration) { self.registration = registration }
        func remove() { registration.remove() }
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
