package com.pluk.reader.domain.remote

import java.io.File

/** Metadatos de un libro en la nube. [id] es el hash del contenido, igual que el identificador local (LIB-003). */
data class RemoteBook(
    val id: String,
    val title: String,
    val authors: List<String>,
    val sizeBytes: Long,
)

/** La nube no se pudo alcanzar (sin red, sin sesión, error del servicio). El libro queda pendiente, no se pierde (SYN-001). */
class RemoteUnavailableException(message: String? = null, cause: Throwable? = null) : Exception(message, cause)

/** Subir el archivo superaría la cuota del plan (LIB-009). Leer y borrar nunca fallan por esto. */
class QuotaExceededException(message: String? = null) : Exception(message)

/**
 * Metadatos de la biblioteca en la nube. Los fallos viajan en el `Result`: nunca lanza, salvo
 * cancelación. El motivo es una [RemoteUnavailableException] u otra excepción para errores inesperados.
 */
interface RemoteLibrary {
    /** Libros vivos (no marcados como borrados, SYN-007) del usuario actual. */
    suspend fun listBooks(): Result<List<RemoteBook>>

    /** Crea o actualiza los metadatos del libro. */
    suspend fun saveBook(book: RemoteBook): Result<Unit>
}

/**
 * Archivos EPUB en la nube. El nombre remoto sale del [bookId]. Los fallos viajan en el `Result`:
 * [QuotaExceededException] si no entra en la cuota, [RemoteUnavailableException] si no hay conexión.
 */
interface BookFileStore {
    /** Sube [file]. Si el archivo ya está en la nube, termina bien sin volver a subirlo (LIB-002). */
    suspend fun upload(bookId: String, file: File): Result<Unit>

    /** Baja el libro a [destination] (un archivo temporal que el llamador mueve al final). */
    suspend fun download(bookId: String, destination: File): Result<Unit>

    /** Sube la portada del libro [bookId] (LIB-012). Si ya está en la nube, termina bien sin volver a subirla. */
    suspend fun uploadCover(bookId: String, file: File): Result<Unit>

    /** Baja la portada a [destination]. `true` si la había; `false` si el libro no tiene portada en la nube. */
    suspend fun downloadCover(bookId: String, destination: File): Result<Boolean>
}
