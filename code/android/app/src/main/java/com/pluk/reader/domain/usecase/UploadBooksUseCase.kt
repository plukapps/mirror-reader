package com.pluk.reader.domain.usecase

import com.pluk.reader.domain.account.QuotaSource
import com.pluk.reader.domain.remote.BookFileStore
import com.pluk.reader.domain.remote.QuotaExceededException
import com.pluk.reader.domain.remote.RemoteLibrary
import com.pluk.reader.domain.remote.RemoteUnavailableException
import com.pluk.reader.domain.repository.BookUploadRepository
import com.pluk.reader.domain.repository.PendingUpload
import javax.inject.Inject

/**
 * Resultado de una tanda de subida. Los libros que no se subieron siguen pendientes y no se pierde nada (SYN-001).
 *
 * @param notEnoughSpace libros que no entraron en la cuota (LIB-009).
 * @param failed libros que fallaron por otro motivo.
 * @param unreachable no se pudo contactar la nube: la tanda se cortó.
 */
data class UploadReport(
    val uploaded: Int = 0,
    val notEnoughSpace: Int = 0,
    val failed: Int = 0,
    val unreachable: Boolean = false,
) {
    val nothingToDo: Boolean get() = this == UploadReport()
}

/**
 * Sube a la nube los libros importados que aún no están (LIB-007). Primero el archivo, que la regla del
 * servidor valida (tipo, tamaño, cuota), y después los metadatos. Reintentar es seguro.
 */
class UploadBooksUseCase @Inject constructor(
    private val uploads: BookUploadRepository,
    private val files: BookFileStore,
    private val library: RemoteLibrary,
    private val quota: QuotaSource,
) {
    private enum class Step { Uploaded, NoSpace, Failed, Unreachable }

    suspend operator fun invoke(): UploadReport {
        val pending = uploads.pending()
        if (pending.isEmpty()) return UploadReport()
        // El servidor suma el uso unos segundos después de cada subida: el espacio libre se lleva aquí.
        var free = quota.current().getOrElse { return UploadReport(unreachable = true) }.freeBytes

        var uploaded = 0
        var noSpace = 0
        var failed = 0
        for (item in pending) {
            if (item.book.sizeBytes > free) {
                noSpace++
                continue
            }
            when (upload(item)) {
                Step.Uploaded -> {
                    uploaded++
                    free -= item.book.sizeBytes
                }
                Step.NoSpace -> noSpace++
                Step.Failed -> failed++
                Step.Unreachable -> return UploadReport(uploaded, noSpace, failed, unreachable = true)
            }
        }
        return UploadReport(uploaded, noSpace, failed)
    }

    private suspend fun upload(item: PendingUpload): Step {
        files.upload(item.book.id, item.file).exceptionOrNull()?.let { return it.toStep() }
        library.saveBook(item.book).exceptionOrNull()?.let { return it.toStep() }
        uploads.markUploaded(item.book.id, item.book.sizeBytes)
        return Step.Uploaded
    }

    private fun Throwable.toStep() = when (this) {
        is RemoteUnavailableException -> Step.Unreachable
        is QuotaExceededException -> Step.NoSpace
        else -> Step.Failed
    }
}
