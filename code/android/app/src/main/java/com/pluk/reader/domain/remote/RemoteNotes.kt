package com.pluk.reader.domain.remote

import com.pluk.reader.domain.repository.SyncNote
import kotlinx.coroutines.flow.Flow

/** Una tanda de notas que cambiaron en la nube. [newestUpdatedAt]: mayor `updatedAt` del servidor, en milisegundos. */
data class RemoteNoteChanges(val notes: List<SyncNote>, val newestUpdatedAt: Long?)

/**
 * Notas en la nube (SYN-016). Los fallos viajan en el `Result`: [RemoteUnavailableException] sin conexión, otra
 * excepción si el servidor la rechaza.
 */
interface RemoteNotes {
    /** Sube la nota. Si la nube ya tiene una edición más reciente no la pisa y termina bien (SYN-005). */
    suspend fun push(note: SyncNote): Result<Unit>

    /** Notas con `updatedAt` posterior a [sinceMillis] (todas si es null), en vivo mientras se recolecta. */
    fun observeChanges(sinceMillis: Long?): Flow<Result<RemoteNoteChanges>>
}

/** Desde dónde pedir los cambios de notas la próxima vez. Se borra al cerrar sesión, con el resto de los datos. */
interface NoteSyncCursor {
    suspend fun lastSeenAt(): Long?

    suspend fun setLastSeenAt(value: Long)
}
