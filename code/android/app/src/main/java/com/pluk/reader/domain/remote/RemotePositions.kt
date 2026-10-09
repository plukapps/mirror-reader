package com.pluk.reader.domain.remote

import com.pluk.reader.domain.model.ReadingPosition
import com.pluk.reader.domain.model.RemotePosition
import kotlinx.coroutines.flow.Flow

/**
 * Lo que trajo una tanda de cambios de la nube. [newestUpdatedAt] es el mayor `updatedAt` del servidor entre ellos,
 * en milisegundos: desde ahí se pide la próxima vez.
 */
data class RemoteChanges(val positions: List<RemotePosition>, val newestUpdatedAt: Long?)

/**
 * Posición de lectura en la nube (SYN-011, SYN-012). Los fallos viajan en el `Result`, como en [RemoteLibrary]:
 * [RemoteUnavailableException] sin conexión, otra excepción si el servidor la rechaza.
 */
interface RemotePositions {
    /**
     * Envía la posición. Si la nube ya tiene una lectura más reciente no la pisa y termina bien: esa llegará
     * por [observeChanges] o [fetch].
     */
    suspend fun push(position: ReadingPosition, deviceId: String, deviceName: String): Result<Unit>

    /** La posición del libro en la nube, directo del servidor, o null si no hay (SYN-012). */
    suspend fun fetch(bookId: String): Result<RemotePosition?>

    /**
     * Cambios de posición con `updatedAt` posterior a [sinceMillis] (todos si es null), en vivo mientras se
     * recolecta. Cada tanda llega como `Result`; un fallo cierra el flujo. No emite las escrituras pendientes
     * de este mismo dispositivo.
     */
    fun observeChanges(sinceMillis: Long?): Flow<Result<RemoteChanges>>
}
