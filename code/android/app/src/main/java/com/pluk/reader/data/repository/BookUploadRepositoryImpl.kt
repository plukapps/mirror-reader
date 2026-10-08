package com.pluk.reader.data.repository

import com.pluk.reader.data.library.LibraryFiles
import com.pluk.reader.data.local.db.BookDao
import com.pluk.reader.data.remote.toRemoteBook
import com.pluk.reader.domain.repository.BookUploadRepository
import com.pluk.reader.domain.repository.PendingUpload
import javax.inject.Inject

class BookUploadRepositoryImpl @Inject constructor(
    private val dao: BookDao,
    private val files: LibraryFiles,
) : BookUploadRepository {
    override suspend fun pending(): List<PendingUpload> = dao.getPendingUpload().mapNotNull { entity ->
        val file = files.bookFile(entity.id)
        // Sin archivo local no hay qué subir. El tamaño sale del archivo, no de la fila (libros anteriores a la v3 tienen 0).
        if (file.isFile) PendingUpload(entity.toRemoteBook(sizeBytes = file.length()), file) else null
    }

    override suspend fun markUploaded(bookId: String, sizeBytes: Long) =
        dao.markUploaded(bookId, uploadedAt = System.currentTimeMillis(), sizeBytes = sizeBytes)
}
