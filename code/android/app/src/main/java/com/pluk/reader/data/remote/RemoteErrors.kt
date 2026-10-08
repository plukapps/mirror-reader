package com.pluk.reader.data.remote

import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.storage.StorageException
import com.pluk.reader.domain.remote.QuotaExceededException
import com.pluk.reader.domain.remote.RemoteUnavailableException
import kotlinx.coroutines.CancellationException
import java.io.IOException

/** Ejecuta una llamada al SDK y devuelve el fallo como `Result` con los tipos del dominio. La cancelación se propaga. */
internal suspend fun <T> remoteCall(block: suspend () -> T): Result<T> = try {
    Result.success(block())
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    Result.failure(e.toRemoteError())
}

/**
 * Traduce los errores del SDK a los del dominio (ver `RemoteLibrary`). Las reglas de Storage solo rechazan
 * una subida del dueño por tipo, tamaño o cuota; la app ya controla los dos primeros, así que `NOT_AUTHORIZED`
 * en una subida es la cuota (por ejemplo, otra subida simultánea la agotó).
 */
internal fun Exception.toRemoteError(): Exception = when {
    this is StorageException && errorCode == StorageException.ERROR_NOT_AUTHORIZED ->
        QuotaExceededException("El servidor rechazó la subida: sin espacio o sin permiso.")
    this is StorageException && errorCode == StorageException.ERROR_RETRY_LIMIT_EXCEEDED ->
        RemoteUnavailableException("Sin conexión con la nube.", this)
    this is FirebaseFirestoreException && (code == FirebaseFirestoreException.Code.UNAVAILABLE ||
        code == FirebaseFirestoreException.Code.DEADLINE_EXCEEDED) ->
        RemoteUnavailableException("Sin conexión con la nube.", this)
    this is IOException -> RemoteUnavailableException("Sin conexión con la nube.", this)
    else -> this
}
