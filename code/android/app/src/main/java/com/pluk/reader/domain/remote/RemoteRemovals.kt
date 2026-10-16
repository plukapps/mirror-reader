package com.pluk.reader.domain.remote

import kotlinx.coroutines.flow.Flow

/**
 * Libros borrados de la cuenta (LIB-020, LIB-008, SYN-007). Los fallos viajan en el `Result`:
 * [RemoteUnavailableException] si no hay conexión o sesión.
 */
interface RemoteRemovals {
    /** Marca el libro como borrado (`deletedAt`). Desde ahí no existe para la cuenta. Repetirlo no hace daño. */
    suspend fun markDeleted(bookId: String): Result<Unit>

    /** Borra el EPUB y la portada de la nube. Lo que ya no está cuenta como borrado. */
    suspend fun deleteFiles(bookId: String): Result<Unit>

    /** Ids de los libros marcados como borrados. */
    suspend fun deletedBookIds(): Result<List<String>>

    /** Los mismos ids cada vez que cambian, mientras se escucha. */
    fun observeDeleted(): Flow<Result<List<String>>>
}
