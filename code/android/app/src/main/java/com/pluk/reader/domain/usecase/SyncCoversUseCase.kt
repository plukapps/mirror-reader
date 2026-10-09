package com.pluk.reader.domain.usecase

import com.pluk.reader.domain.remote.BookFileStore
import com.pluk.reader.domain.remote.RemoteUnavailableException
import com.pluk.reader.domain.repository.CloudBooksRepository
import javax.inject.Inject

/** Cuántas portadas se subieron y se bajaron en una pasada. */
data class CoverSyncReport(val uploaded: Int = 0, val downloaded: Int = 0)

/**
 * Hace que la portada de cada libro se vea en todos los dispositivos (LIB-012): sube las portadas locales de
 * libros ya subidos que aún no están anotadas como subidas (también las de libros viejos que se subieron sin ella) y baja las de los libros que están solo
 * en la nube. Es secundario: un fallo en una portada no corta las demás y nunca afecta a leer o descargar.
 * Sin conexión corta la pasada.
 */
class SyncCoversUseCase @Inject constructor(
    private val cloud: CloudBooksRepository,
    private val files: BookFileStore,
) {
    suspend operator fun invoke(): CoverSyncReport {
        var uploaded = 0
        for (cover in cloud.coversToUpload()) {
            val result = files.uploadCover(cover.bookId, cover.file)
            if (result.exceptionOrNull() is RemoteUnavailableException) return CoverSyncReport(uploaded)
            if (result.isSuccess) {
                // Se anota para no consultar la nube por esta portada en las próximas sincronizaciones.
                cloud.markCoverUploaded(cover.bookId)
                uploaded++
            }
        }
        var downloaded = 0
        for (bookId in cloud.cloudBooksWithoutCover()) {
            val temp = cloud.newTempFile()
            val result = files.downloadCover(bookId, temp)
            if (result.getOrNull() == true) {
                cloud.installCover(bookId, temp)
                downloaded++
            } else {
                temp.delete()
                if (result.exceptionOrNull() is RemoteUnavailableException) return CoverSyncReport(uploaded, downloaded)
            }
        }
        return CoverSyncReport(uploaded, downloaded)
    }
}
