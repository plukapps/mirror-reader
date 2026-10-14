import FirebaseFirestore
import Foundation
import ReaderDomain

/// Notas en `users/{uid}/notes/{noteId}` (SYN-016), como `FirestoreNotes` de Android. Las reglas exigen `updatedAt` del
/// servidor, que `editedAt` no retroceda y que la cita y el lugar no cambien (ver `backend.md`).
struct FirestoreNotes: RemoteNotes {
    func push(_ note: SyncNote) async throws {
        let db = Firestore.firestore()
        let ref = db.document("users/\(try currentUid())/notes/\(note.note.id)")
        let data = Self.data(note)
        do {
            // Una transacción, no `setData`: sin conexión falla enseguida en lugar de quedar esperando.
            _ = try await db.runTransaction { transaction, errorPointer in
                let existing: Int64?
                do {
                    let snapshot = try transaction.getDocument(ref)
                    existing = snapshot.exists ? (snapshot.get(Field.editedAt) as? NSNumber)?.int64Value : nil
                } catch {
                    errorPointer?.pointee = error as NSError
                    return nil
                }
                // SYN-005: la nube tiene una edición más nueva: no se pisa; llega por la escucha.
                if existing == nil || existing! <= note.editedAt {
                    transaction.setData(data, forDocument: ref)
                }
                return nil
            }
        } catch {
            throw remoteError(error)
        }
    }

    func observeChanges(since: Int64?) -> AsyncThrowingStream<RemoteNoteChanges, any Error> {
        AsyncThrowingStream { continuation in
            let uid: String
            do {
                uid = try currentUid()
            } catch {
                continuation.finish(throwing: error)
                return
            }
            let collection = Firestore.firestore().collection("users/\(uid)/notes")
            let query: Query = since.map {
                collection.whereField(Field.updatedAt, isGreaterThan: Timestamp(date: Date(timeIntervalSince1970: Double($0) / 1000)))
            } ?? collection
            let registration = query.addSnapshotListener { snapshot, error in
                guard let snapshot else {
                    continuation.finish(throwing: error.map(remoteError) ?? RemoteUnavailableError("Sin datos de la nube."))
                    return
                }
                // Las escrituras de este dispositivo que el servidor aún no confirmó se omiten.
                let docs = snapshot.documentChanges
                    .filter { $0.type == .added || $0.type == .modified }
                    .map(\.document)
                    .filter { !$0.metadata.hasPendingWrites }
                guard !docs.isEmpty else { return }
                let newest = docs.compactMap { ($0.get(Field.updatedAt) as? Timestamp)?.dateValue() }
                    .map { Int64(($0.timeIntervalSince1970 * 1000).rounded()) }
                    .max()
                continuation.yield(RemoteNoteChanges(notes: docs.compactMap(Self.syncNote), newestUpdatedAt: newest))
            }
            let listener = ListenerBox(registration)
            continuation.onTermination = { _ in listener.remove() }
        }
    }

    private static func data(_ note: SyncNote) -> [String: Any] {
        [
            Field.bookId: note.note.bookId,
            Field.locatorJson: note.note.locatorJson,
            Field.quote: note.note.quote,
            Field.text: note.note.text,
            Field.progression: min(max(note.note.progression, 0), 1),
            Field.createdAt: note.note.createdAt,
            Field.editedAt: note.editedAt,
            Field.deletedAt: note.deletedAt.map { NSNumber(value: $0) as Any } ?? NSNull(),
            Field.updatedAt: FieldValue.serverTimestamp(),
        ]
    }

    private static func syncNote(_ snapshot: DocumentSnapshot) -> SyncNote? {
        guard snapshot.exists,
              let bookId = snapshot.get(Field.bookId) as? String,
              let locator = snapshot.get(Field.locatorJson) as? String,
              let quote = snapshot.get(Field.quote) as? String,
              let createdAt = (snapshot.get(Field.createdAt) as? NSNumber)?.int64Value,
              let editedAt = (snapshot.get(Field.editedAt) as? NSNumber)?.int64Value
        else { return nil }
        let note = Note(
            id: snapshot.documentID, bookId: bookId, locatorJson: locator, quote: quote,
            text: (snapshot.get(Field.text) as? String) ?? "",
            progression: (snapshot.get(Field.progression) as? NSNumber)?.doubleValue ?? 0,
            createdAt: createdAt, updatedAt: editedAt
        )
        return SyncNote(note: note, deletedAt: (snapshot.get(Field.deletedAt) as? NSNumber)?.int64Value)
    }

    /// El registro del SDK no se declara `Sendable`, pero `remove()` se puede llamar desde cualquier hilo.
    private final class ListenerBox: @unchecked Sendable {
        private let registration: any ListenerRegistration
        init(_ registration: any ListenerRegistration) { self.registration = registration }
        func remove() { registration.remove() }
    }

    private enum Field {
        static let bookId = "bookId"
        static let locatorJson = "locatorJson"
        static let quote = "quote"
        static let text = "text"
        static let progression = "progression"
        static let createdAt = "createdAt"
        static let editedAt = "editedAt"
        static let deletedAt = "deletedAt"
        static let updatedAt = "updatedAt"
    }
}
