package com.pluk.reader.domain.remote

import kotlinx.coroutines.flow.Flow

/** Libros de la cuenta que cambian en la nube, para enterarse sin esperar a la próxima pasada (SYN-015). */
fun interface RemoteBookChanges {
    /**
     * Ids de los libros vivos (sin `deletedAt`) cambiados después de [sinceMillis], cada vez que llega algo nuevo,
     * mientras se escucha. Los fallos viajan en el `Result`.
     */
    fun observeAliveSince(sinceMillis: Long): Flow<Result<List<String>>>
}
