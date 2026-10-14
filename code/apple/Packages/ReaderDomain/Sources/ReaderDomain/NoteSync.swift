import Foundation

/// Una nota tal como viaja a la nube o viene de ella (SYN-016). `deletedAt` no es nil si está borrada (SYN-007).
public struct SyncNote: Equatable, Sendable {
    public let note: Note
    public let deletedAt: Int64?

    public init(note: Note, deletedAt: Int64? = nil) {
        self.note = note
        self.deletedAt = deletedAt
    }

    /// Última edición o borrado, reloj del dispositivo (`editedAt` en la nube): decide quién gana (SYN-005).
    public var editedAt: Int64 { note.updatedAt }
}

/// Lado local de la sincronización de notas (SYN-016), como `NoteSyncStore` de Android.
public protocol NoteSyncStore: Sendable {
    /// Cambios que la nube no tiene y se pueden subir (el libro de una nota viva ya está en la nube).
    func pendingNotes() async -> [SyncNote]
    /// PRF-009: hay notas que se perderían al cerrar sesión. `syncOn`: con la sincronización apagada, cuentan todas.
    func hasUnsyncedNotes(syncOn: Bool) async -> Bool
    /// La nube ya tiene la versión de `editedAt`. Si mientras tanto se editó, sigue pendiente.
    func markNoteSynced(id: String, editedAt: Int64) async
    /// Aplica una nota de la nube solo si es más reciente que la local (SYN-005); un borrado la elimina (SYN-007).
    func applyRemote(_ remote: SyncNote) async
    /// Elimina las borradas que la nube ya tiene borradas.
    func purgeSyncedNoteDeletions() async
}
