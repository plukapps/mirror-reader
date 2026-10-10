import FirebaseFirestore
import Foundation
import ReaderDomain

/// Lee `users/{uid}`: `usedBytes` lo mantiene el servidor y `quotaBytes` lo define el plan (ADR 0008).
struct FirestoreQuotaSource: QuotaSource {
    func current() async throws -> StorageQuota {
        let uid = try currentUid()
        let snapshot: DocumentSnapshot
        do {
            // Siempre del servidor: un valor viejo dejaría subir de más o bloquearía sin motivo.
            snapshot = try await Firestore.firestore().document("users/\(uid)").getDocument(source: .server)
        } catch {
            throw remoteError(error)
        }
        // Si el documento aún no existe, la función de alta no terminó: se reintenta más tarde.
        guard snapshot.exists,
              let used = (snapshot.get("usedBytes") as? NSNumber)?.int64Value,
              let quota = (snapshot.get("quotaBytes") as? NSNumber)?.int64Value
        else { throw RemoteUnavailableError("La cuenta todavía no está lista.") }
        return StorageQuota(usedBytes: used, quotaBytes: quota)
    }
}
