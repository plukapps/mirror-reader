package com.pluk.reader.data.repository

import android.net.Uri
import com.pluk.reader.data.library.EpubImporter
import com.pluk.reader.data.library.LibraryFiles
import com.pluk.reader.data.local.db.BookDao
import com.pluk.reader.data.remote.toCloudOnlyEntity
import com.pluk.reader.domain.model.BookOpenException
import com.pluk.reader.domain.model.ImportOutcome
import com.pluk.reader.domain.remote.RemoteBook
import com.pluk.reader.domain.repository.CloudBooksRepository
import com.pluk.reader.domain.repository.CoverFile
import java.io.File
import javax.inject.Inject

class CloudBooksRepositoryImpl @Inject constructor(
    private val dao: BookDao,
    private val files: LibraryFiles,
    private val importer: EpubImporter,
) : CloudBooksRepository {

    override suspend fun addCloudOnly(books: List<RemoteBook>) {
        val now = System.currentTimeMillis()
        // El orden de la nube se conserva: el primero de la lista queda como el más reciente.
        dao.insertAll(books.mapIndexed { index, book -> book.toCloudOnlyEntity(now - index) })
    }

    override suspend fun markCoverUploaded(bookId: String) = dao.markCoverUploaded(bookId)

    override suspend fun coversToUpload(): List<CoverFile> = dao.getCoversToUpload().mapNotNull {
        val file = files.coverFile(it.id)
        if (file.isFile) CoverFile(it.id, file) else null
    }

    override suspend fun cloudBooksWithoutCover(): List<String> = dao.getCloudOnlyWithoutCover()

    override suspend fun installCover(bookId: String, downloaded: File) {
        val target = files.coverFile(bookId)
        if (downloaded.renameTo(target)) dao.markCover(bookId) else downloaded.delete()
    }

    override suspend fun cloudOnlyBookIds(): List<String> = dao.getCloudOnlyIds()

    override suspend fun isDownloaded(bookId: String) = dao.get(bookId)?.isDownloaded == true

    override fun newTempFile(): File = files.newTempFile()

    override suspend fun install(bookId: String, downloaded: File): Result<Unit> {
        // El importador ya sabe completar un libro que estaba solo en la nube: valida, guarda el archivo y la portada.
        val outcome = try {
            importer.import(Uri.fromFile(downloaded))
        } finally {
            downloaded.delete()
        }
        return when (outcome) {
            is ImportOutcome.Imported -> if (outcome.bookId == bookId) {
                Result.success(Unit)
            } else {
                Result.failure(BookOpenException("El archivo descargado no coincide con el libro."))
            }
            is ImportOutcome.AlreadyInLibrary -> Result.success(Unit)
            is ImportOutcome.Rejected -> Result.failure(BookOpenException(outcome.message))
        }
    }
}
