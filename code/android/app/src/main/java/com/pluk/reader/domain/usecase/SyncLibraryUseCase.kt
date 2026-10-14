package com.pluk.reader.domain.usecase

import com.pluk.reader.domain.remote.RemoteUnavailableException
import com.pluk.reader.domain.repository.CloudBooksRepository
import com.pluk.reader.domain.sync.SyncGate
import com.pluk.reader.domain.sync.WaitingForWifiException
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
    /** PRF-023: "Solo con Wi-Fi" y sin Wi-Fi; la pasada se cortó sin tocar la nube. */
    val waitingForWifi: Boolean = false,
) {
    val offline: Boolean get() = unreachable || upload.unreachable || waitingForWifi
    val notEnoughSpace: Int get() = upload.notEnoughSpace
    val failed: Int get() = upload.failed + downloadFailed
}

/**
 * Deja la biblioteca igual en la nube y en este dispositivo (SYN-001, LIB-007): trae los libros que hay en la nube,
 * sube los importados que faltan (LIB-009 decide cuáles entran en la cuota), sincroniza las portadas (LIB-012) y baja
 * los archivos que aún no están aquí, y envía las posiciones de lectura y las notas pendientes. Antes envía los libros quitados aquí y
 * quita los borrados en otros dispositivos (LIB-020). Cada paso se puede repetir sin duplicar nada. Sin conexión corta la pasada.
 */
class SyncLibraryUseCase @Inject constructor(
    private val syncRemoteBooks: SyncRemoteBooksUseCase,
    private val uploadBooks: UploadBooksUseCase,
    private val syncCovers: SyncCoversUseCase,
    private val downloadBook: DownloadBookUseCase,
    private val cloud: CloudBooksRepository,
    private val positions: PositionFlusher,
    private val removals: CloudRemovals,
    private val notes: NoteFlusher,
    private val gate: SyncGate = SyncGate.ALWAYS_OPEN,
) {
    suspend operator fun invoke(): SyncReport {
        // PRF-023: sin Wi-Fi (con "Solo con Wi-Fi") no se toca la nube. Se vuelve a mirar entre pasos por si la red
        // cambia a mitad de la pasada.
        val waiting = SyncReport(waitingForWifi = true)
        if (!gate.isOpen()) return waiting
        // LIB-020, LIB-008: primero los borrados, así no se baja un libro que ya no existe en la cuenta.
        when (removals.sync().exceptionOrNull()) {
            is RemoteUnavailableException -> return SyncReport(unreachable = true)
            is WaitingForWifiException -> return waiting
        }
        if (syncRemoteBooks().exceptionOrNull() is RemoteUnavailableException) return SyncReport(unreachable = true)

        if (!gate.isOpen()) return waiting
        val upload = uploadBooks()
        if (upload.unreachable) return SyncReport(upload)

        if (!gate.isOpen()) return SyncReport(upload, waitingForWifi = true)
        syncCovers()

        var downloaded = 0
        var failed = 0
        for (bookId in cloud.cloudOnlyBookIds()) {
            val failure = downloadBook(bookId).exceptionOrNull()
            when {
                failure == null -> downloaded++
                failure.cause is WaitingForWifiException -> return SyncReport(upload, downloaded, failed, waitingForWifi = true)
                failure.cause is RemoteUnavailableException -> return SyncReport(upload, downloaded, failed, unreachable = true)
                else -> failed++
            }
        }
        // Los libros recién subidos ya existen en la nube: ahora puede salir la posición de lectura que estaba
        // esperándolos (SYN-011).
        positions.flush()
        // SYN-016: igual con las notas de los libros recién subidos. Si la función está apagada, no hace nada (SYN-017).
        notes.flush()
        return SyncReport(upload, downloaded, failed)
    }
}
