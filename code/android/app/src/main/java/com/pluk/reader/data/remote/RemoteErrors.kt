package com.pluk.reader.data.remote

import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.storage.StorageException
import com.pluk.reader.domain.remote.QuotaExceededException
import com.pluk.reader.domain.remote.RemoteUnavailableException
import kotlinx.coroutines.CancellationException
import java.io.IOException

import android.util.Log

private const val REMOTE_LOG_TAG = "RemoteRequest"

/** Ejecuta una llamada al SDK, registra la petición con tiempo de ejecución y devuelve el fallo como `Result`. La cancelación se propaga. */
internal suspend fun <T> remoteCall(
    operationName: String = "RemoteCall",
    block: suspend () -> T,
): Result<T> {
    val start = System.currentTimeMillis()
    Log.d(REMOTE_LOG_TAG, "--> $operationName")
    return try {
        val result = block()
        val duration = System.currentTimeMillis() - start
        Log.d(REMOTE_LOG_TAG, "<-- $operationName (${duration}ms)")
        Result.success(result)
    } catch (e: CancellationException) {
        val duration = System.currentTimeMillis() - start
        Log.d(REMOTE_LOG_TAG, "<-- $operationName cancelada (${duration}ms)")
        throw e
    } catch (e: Exception) {
        val duration = System.currentTimeMillis() - start
        val remoteError = e.toRemoteError()
        Log.w(REMOTE_LOG_TAG, "<-- $operationName falló (${duration}ms): ${remoteError.message ?: remoteError}", remoteError)
        Result.failure(remoteError)
    }
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
