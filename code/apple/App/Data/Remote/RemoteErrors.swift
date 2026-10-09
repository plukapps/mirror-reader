import FirebaseAuth
import FirebaseFirestore
import FirebaseStorage
import Foundation
import ReaderDomain

/// Convierte los errores de Firebase que significan "no se pudo llegar a la nube" en `RemoteUnavailableError`,
/// como `RemoteErrors.kt` de Android. El resto se propaga tal cual.
func remoteError(_ error: Error) -> Error {
    let nsError = error as NSError
    let unavailable: Bool = switch nsError.domain {
    case NSURLErrorDomain: true
    case FirestoreErrorDomain:
        [FirestoreErrorCode.unavailable.rawValue, FirestoreErrorCode.deadlineExceeded.rawValue,
         FirestoreErrorCode.unauthenticated.rawValue].contains(nsError.code)
    case AuthErrorDomain:
        nsError.code == AuthErrorCode.networkError.rawValue
    case StorageErrorDomain:
        [StorageErrorCode.retryLimitExceeded.rawValue, StorageErrorCode.unauthenticated.rawValue].contains(nsError.code)
    default: false
    }
    return unavailable ? RemoteUnavailableError(nsError.localizedDescription) : error
}

/// `uid` del usuario con sesión, o `RemoteUnavailableError` si no hay sesión.
func currentUid() throws -> String {
    guard let uid = Auth.auth().currentUser?.uid else { throw RemoteUnavailableError("No hay sesión iniciada.") }
    return uid
}
