package com.pluk.reader.domain.usecase

import com.pluk.reader.domain.model.BookOpenException
import com.pluk.reader.domain.remote.BookFileStore
import com.pluk.reader.domain.remote.RemoteUnavailableException
import com.pluk.reader.domain.repository.CloudBooksRepository
import com.pluk.reader.domain.sync.SyncGate
import com.pluk.reader.domain.sync.WaitingForWifiException
import javax.inject.Inject

/**
 * Descarga un libro que está solo en la nube (LIB-007). Si el archivo ya está en el dispositivo no hace nada.
 * Baja a un temporal y recién al terminar lo instala: una descarga cortada no deja un libro a medias.
 * El fallo es una [BookOpenException] con mensaje apto para el usuario.
 */
class DownloadBookUseCase @Inject constructor(
    private val cloud: CloudBooksRepository,
    private val files: BookFileStore,
    private val gate: SyncGate = SyncGate.ALWAYS_OPEN,
) {
    suspend operator fun invoke(bookId: String): Result<Unit> {
        if (cloud.isDownloaded(bookId)) return Result.success(Unit)
        // PRF-023: con "Solo con Wi-Fi" y sin Wi-Fi, no se baja.
        if (!gate.isOpen()) return Result.failure(BookOpenException(WIFI_ONLY_MESSAGE, WaitingForWifiException()))
        val temp = cloud.newTempFile()
        files.download(bookId, temp).onFailure {
            temp.delete()
            return Result.failure(it.toOpenError())
        }
        return cloud.install(bookId, temp)
    }

    companion object {
        const val WIFI_ONLY_MESSAGE = "Este libro se descarga con Wi-Fi. Podés cambiarlo en Perfil."
    }

    private fun Throwable.toOpenError() = BookOpenException(
        if (this is RemoteUnavailableException) {
            "Sin conexión. No se pudo descargar el libro."
        } else {
            "No se pudo descargar el libro."
        },
        this,
    )
}
