import FirebaseFirestore
import Foundation
import ReaderDomain

/// Posiciones en `users/{uid}/positions/{bookId}` (ADR 0011), como `FirestorePositions` de Android. Las reglas exigen
/// `updatedAt` del servidor y que `readAt` no retroceda (ver `backend.md`).
struct FirestorePositions: RemotePositions {
    func push(_ position: ReadingPosition, deviceId: String, deviceName: String) async throws {
        let db = Firestore.firestore()
        let ref = db.document("users/\(try currentUid())/positions/\(position.bookId)")
        do {
            // Una transacción, no `setData`: sin conexión falla enseguida en lugar de quedar esperando.
            _ = try await db.runTransaction { transaction, errorPointer in
                let existing: Int64?
                do {
                    let snapshot = try transaction.getDocument(ref)
                    existing = snapshot.exists ? (snapshot.get(Field.readAt) as? NSNumber)?.int64Value : nil
                } catch {
                    errorPointer?.pointee = error as NSError
                    return nil
                }
                // Hay una lectura más nueva en la nube: no se pisa; llegará por la escucha o al abrir el libro.
                if existing == nil || existing! <= position.readAt {
                    transaction.setData(
                        [
                            Field.locatorJson: position.locatorJson,
                            Field.progress: position.progress.map { NSNumber(value: $0) as Any } ?? NSNull(),
                            Field.readAt: position.readAt,
                            Field.deviceId: deviceId,
                            Field.deviceName: deviceName,
                            Field.updatedAt: FieldValue.serverTimestamp(),
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

    func fetch(bookId: String) async throws -> RemotePosition? {
        do {
            let snapshot = try await Firestore.firestore().document("users/\(try currentUid())/positions/\(bookId)")
                .getDocument(source: .server)
            return Self.remotePosition(snapshot)
        } catch {
            throw remoteError(error)
        }
    }

    func observeChanges(since: Int64?) -> AsyncThrowingStream<RemoteChanges, any Error> {
        AsyncThrowingStream { continuation in
            let uid: String
            do {
                uid = try currentUid()
            } catch {
                continuation.finish(throwing: error)
                return
            }
            let collection = Firestore.firestore().collection("users/\(uid)/positions")
            let query: Query = since.map {
                collection.whereField(Field.updatedAt, isGreaterThan: Timestamp(date: Date(timeIntervalSince1970: Double($0) / 1000)))
            } ?? collection
            let registration = query.addSnapshotListener { snapshot, error in
                guard let snapshot else {
                    continuation.finish(throwing: error.map(remoteError) ?? RemoteUnavailableError("Sin datos de la nube."))
                    return
                }
                // Las escrituras de este dispositivo que el servidor aún no confirmó llegan sin `updatedAt`: se omiten.
                let docs = snapshot.documentChanges
                    .filter { $0.type == .added || $0.type == .modified }
                    .map(\.document)
                    .filter { !$0.metadata.hasPendingWrites }
                guard !docs.isEmpty else { return }
                let newest = docs.compactMap { ($0.get(Field.updatedAt) as? Timestamp)?.dateValue() }
                    .map { Int64(($0.timeIntervalSince1970 * 1000).rounded()) }
                    .max()
                continuation.yield(RemoteChanges(positions: docs.compactMap(Self.remotePosition), newestUpdatedAt: newest))
            }
            let listener = ListenerBox(registration)
            continuation.onTermination = { _ in listener.remove() }
        }
    }

    private static func remotePosition(_ snapshot: DocumentSnapshot) -> RemotePosition? {
        guard snapshot.exists,
              let locator = snapshot.get(Field.locatorJson) as? String,
              let readAt = (snapshot.get(Field.readAt) as? NSNumber)?.int64Value
        else { return nil }
        return RemotePosition(
            position: ReadingPosition(
                bookId: snapshot.documentID,
                locatorJson: locator,
                progress: (snapshot.get(Field.progress) as? NSNumber)?.doubleValue,
                readAt: readAt
            ),
            deviceId: (snapshot.get(Field.deviceId) as? String) ?? "",
            deviceName: (snapshot.get(Field.deviceName) as? String) ?? ""
        )
    }

    /// El registro del SDK no se declara `Sendable`, pero `remove()` se puede llamar desde cualquier hilo.
    private final class ListenerBox: @unchecked Sendable {
        private let registration: any ListenerRegistration
        init(_ registration: any ListenerRegistration) { self.registration = registration }
        func remove() { registration.remove() }
    }

    private enum Field {
        static let locatorJson = "locatorJson"
        static let progress = "progress"
        static let readAt = "readAt"
        static let deviceId = "deviceId"
        static let deviceName = "deviceName"
        static let updatedAt = "updatedAt"
    }
}
