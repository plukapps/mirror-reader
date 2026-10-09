package com.pluk.reader.domain.usecase

import com.pluk.reader.domain.remote.RemoteUnavailableException
import com.pluk.reader.domain.repository.CloudBooksRepository
import javax.inject.Inject

/**
 * Resultado de una pasada de sincronización de la biblioteca. Lo que no se pudo hacer sigue pendiente y se
 * reintenta en la próxima pasada (SYN-001).
 *
 * @param upload cómo salió la subida de los libros del dispositivo.
 * @param downloaded libros de la nube que se bajaron al dispositivo.
 * @param downloadFailed libros que no se pudieron bajar por otro motivo que la falta de conexión.
 * @param unreachable no se pudo contactar la nube: la pasada se cortó.
 */
data class SyncReport(
    val upload: UploadReport = UploadReport(),
    val downloaded: Int = 0,
    val downloadFailed: Int = 0,
    val unreachable: Boolean = false,
) {
    val offline: Boolean get() = unreachable || upload.unreachable
    val notEnoughSpace: Int get() = upload.notEnoughSpace
    val failed: Int get() = upload.failed + downloadFailed
}

/**
 * Deja la biblioteca igual en la nube y en este dispositivo (SYN-001, LIB-007): trae los libros que hay en la nube,
 * sube los importados que faltan (LIB-009 decide cuáles entran en la cuota), sincroniza las portadas (LIB-012) y baja
 * los archivos que aún no están aquí. Cada paso se puede repetir sin duplicar nada. Sin conexión corta la pasada.
 */
class SyncLibraryUseCase @Inject constructor(
    private val syncRemoteBooks: SyncRemoteBooksUseCase,
    private val uploadBooks: UploadBooksUseCase,
    private val syncCovers: SyncCoversUseCase,
    private val downloadBook: DownloadBookUseCase,
    private val cloud: CloudBooksRepository,
) {
    suspend operator fun invoke(): SyncReport {
        if (syncRemoteBooks().exceptionOrNull() is RemoteUnavailableException) return SyncReport(unreachable = true)

        val upload = uploadBooks()
        if (upload.unreachable) return SyncReport(upload)

        syncCovers()

        var downloaded = 0
        var failed = 0
        for (bookId in cloud.cloudOnlyBookIds()) {
            val failure = downloadBook(bookId).exceptionOrNull()
            when {
                failure == null -> downloaded++
                failure.cause is RemoteUnavailableException -> return SyncReport(upload, downloaded, failed, unreachable = true)
                else -> failed++
            }
        }
        return SyncReport(upload, downloaded, failed)
    }
}
