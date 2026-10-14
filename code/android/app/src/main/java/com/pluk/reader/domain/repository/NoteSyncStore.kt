package com.pluk.reader.domain.repository

import com.pluk.reader.domain.model.Note

/** Una nota tal como viaja a la nube o viene de ella (SYN-016). [deletedAt] no es null si está borrada (SYN-007). */
data class SyncNote(val note: Note, val deletedAt: Long? = null) {
    /** Última edición o borrado, reloj del dispositivo: decide quién gana (SYN-005). */
    val editedAt: Long get() = note.updatedAt
}

/** Lado local de la sincronización de notas (SYN-016). */
interface NoteSyncStore {
    /** Cambios que la nube no tiene y se pueden subir (el libro de una nota viva ya está en la nube). */
    suspend fun pending(): List<SyncNote>

    /** PRF-009: hay notas que se perderían al cerrar sesión. [syncOn]: con la sincronización apagada, cuentan todas. */
    suspend fun hasUnsynced(syncOn: Boolean): Boolean

    /** La nube ya tiene la versión de [editedAt]. Si mientras tanto se editó, sigue pendiente. */
    suspend fun markSynced(id: String, editedAt: Long)

    /** Aplica una nota de la nube solo si es más reciente que la local (SYN-005); un borrado la elimina (SYN-007). */
    suspend fun applyRemote(remote: SyncNote)

    /** Elimina las borradas que la nube ya tiene borradas. */
    suspend fun purgeSyncedDeletions()
}
