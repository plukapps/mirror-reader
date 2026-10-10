import Foundation

/// Posición de lectura de un libro (RDR-006). El locator viaja serializado: el dominio no lo interpreta.
public struct ReadingPosition: Equatable, Sendable {
    public let bookId: String
    public let locatorJson: String
    /// Progresión total de 0 a 1, o nil si se desconoce.
    public let progress: Double?
    /// Milisegundos desde 1970 en que se leyó, según el reloj del dispositivo. Decide cuál gana (SYN-003).
    public let readAt: Int64

    public init(bookId: String, locatorJson: String, progress: Double?, readAt: Int64) {
        self.bookId = bookId
        self.locatorJson = locatorJson
        self.progress = progress
        self.readAt = readAt
    }
}

/// La posición guardada en este dispositivo. `isSynced`: la nube ya tiene esta lectura.
public struct LocalPosition: Equatable, Sendable {
    public let position: ReadingPosition
    public let isSynced: Bool

    public init(position: ReadingPosition, isSynced: Bool) {
        self.position = position
        self.isSynced = isSynced
    }
}

/// La posición que hay en la nube (`users/{uid}/positions/{bookId}`, ADR 0011), con el dispositivo que la escribió.
public struct RemotePosition: Equatable, Sendable {
    public let position: ReadingPosition
    public let deviceId: String
    public let deviceName: String

    public init(position: ReadingPosition, deviceId: String, deviceName: String) {
        self.position = position
        self.deviceId = deviceId
        self.deviceName = deviceName
    }
}

/// Qué hacer con la posición de un libro al conocer la de la nube (SYN-003).
public enum PositionMerge: Equatable, Sendable {
    /// La de la nube es más reciente: reemplaza a la local.
    case useRemote
    /// La local es más reciente, o aún no se envió: hay que enviarla.
    case pushLocal
    /// Son la misma lectura (por ejemplo el eco de lo que este dispositivo envió): solo marcar la local como enviada.
    case markSynced
    case nothing
}

/// Gana la lectura más reciente por `readAt`, no la que llegó último (SYN-003), como `mergePosition` de Android. Una
/// lectura local más vieja que la de la nube se reemplaza: la más nueva la supera, no es un dato que se pierda
/// (SYN-010).
public func mergePosition(local: LocalPosition?, remote: RemotePosition?) -> PositionMerge {
    guard let remote else {
        if let local, !local.isSynced { return .pushLocal }
        return .nothing
    }
    guard let local else { return .useRemote }
    if remote.position.readAt > local.position.readAt { return .useRemote }
    if remote.position.readAt < local.position.readAt { return .pushLocal }
    return local.isSynced ? .nothing : .markSynced
}

/// Fracción del libro (2 %) a partir de la cual se pregunta antes de saltar a la posición de otro dispositivo (SYN-003).
public let jumpConfirmThreshold = 0.02

/// Fracción del libro (0,5 %) a partir de la cual se avisa, mientras se lee, que otro dispositivo va más adelante
/// (SYN-013).
public let continueNoticeThreshold = 0.005

// La tolerancia evita que 0.52 - 0.50 (0.020000000000000018) cuente como más del 2 %.
private let epsilon = 1e-9

/// La distancia `gap` (fracción del libro, sin signo) es mayor que el umbral de `jumpConfirmThreshold`.
public func exceedsJumpThreshold(_ gap: Double) -> Bool {
    gap > jumpConfirmThreshold + epsilon
}

/// SYN-013: mientras se lee se ofrece seguir desde otro dispositivo solo si leyó más adelante que la posición actual,
/// por más del 0,5 % del libro. Sin el avance de la lectura remota no se ofrece; sin el de la actual, sí.
public func shouldOfferJump(currentProgress: Double?, remote: RemotePosition) -> Bool {
    guard let ahead = remote.position.progress else { return false }
    guard let currentProgress else { return true }
    return ahead - currentProgress > continueNoticeThreshold + epsilon
}
