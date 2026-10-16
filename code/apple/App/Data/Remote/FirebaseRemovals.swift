import FirebaseFirestore
import FirebaseStorage
import Foundation
import ReaderDomain

/// Borrar un libro de la cuenta (LIB-020), como `FirebaseRemovals` de Android: `deletedAt` en
/// `users/{uid}/books/{id}` (las reglas no permiten borrar el documento, SYN-007) y los archivos de Storage. La
/// función `onBookFileDeleted` descuenta la cuota.
struct FirebaseRemovals: RemoteRemovals {
    func markDeleted(bookId: String) async throws {
        let ref = Firestore.firestore().document("users/\(try currentUid())/books/\(bookId)")
        do {
            try await ref.updateData([Field.deletedAt: FieldValue.serverTimestamp(), Field.updatedAt: FieldValue.serverTimestamp()])
        } catch {
            // Sin documento no hay nada que marcar.
            let nsError = error as NSError
            if nsError.domain == FirestoreErrorDomain, nsError.code == FirestoreErrorCode.notFound.rawValue { return }
            throw remoteError(error)
        }
    }

    func deleteFiles(bookId: String) async throws {
        let uid = try currentUid()
        try await deleteIfExists(Storage.storage().reference(withPath: "users/\(uid)/books/\(bookId).epub"))
        try await deleteIfExists(Storage.storage().reference(withPath: "users/\(uid)/covers/\(bookId).jpg"))
    }

    func deletedBookIds() async throws -> [String] {
        do {
            return try await deletedQuery(uid: try currentUid()).getDocuments(source: .server).documents.map(\.documentID)
        } catch {
            throw remoteError(error)
        }
    }

    func observeDeleted() -> AsyncThrowingStream<[String], any Error> {
        AsyncThrowingStream { continuation in
            let uid: String
            do {
                uid = try currentUid()
            } catch {
                continuation.finish(throwing: error)
                return
            }
            let registration = deletedQuery(uid: uid).addSnapshotListener { snapshot, error in
                guard let snapshot else {
                    continuation.finish(throwing: error.map(remoteError) ?? RemoteUnavailableError("Sin datos de la nube."))
                    return
                }
                continuation.yield(snapshot.documents.map(\.documentID))
            }
            let listener = ListenerBox(registration)
            continuation.onTermination = { _ in listener.remove() }
        }
    }

    private func deletedQuery(uid: String) -> Query {
        Firestore.firestore().collection("users/\(uid)/books").whereField(Field.deletedAt, isNotEqualTo: NSNull())
    }

    private func deleteIfExists(_ ref: StorageReference) async throws {
        do {
            try await ref.delete()
        } catch {
            if isObjectNotFound(error) { return }
            throw remoteError(error)
        }
    }

    private enum Field {
        static let deletedAt = "deletedAt"
        static let updatedAt = "updatedAt"
    }

    /// El registro del SDK no se declara `Sendable`, pero `remove()` se puede llamar desde cualquier hilo.
    private final class ListenerBox: @unchecked Sendable {
        private let registration: any ListenerRegistration
        init(_ registration: any ListenerRegistration) { self.registration = registration }
        func remove() { registration.remove() }
    }
}
