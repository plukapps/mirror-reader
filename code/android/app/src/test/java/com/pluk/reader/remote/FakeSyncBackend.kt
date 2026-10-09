package com.pluk.reader.remote

import com.pluk.reader.domain.account.QuotaSource
import com.pluk.reader.domain.account.StorageQuota
import com.pluk.reader.domain.remote.BookFileStore
import com.pluk.reader.domain.remote.RemoteBook
import com.pluk.reader.domain.remote.RemoteLibrary
import com.pluk.reader.domain.remote.RemoteUnavailableException
import com.pluk.reader.domain.repository.BookUploadRepository
import com.pluk.reader.domain.repository.CloudBooksRepository
import com.pluk.reader.domain.repository.CoverFile
import com.pluk.reader.domain.repository.PendingUpload
import com.pluk.reader.domain.usecase.DownloadBookUseCase
import com.pluk.reader.domain.usecase.SyncCoversUseCase
import com.pluk.reader.domain.usecase.SyncLibraryUseCase
import com.pluk.reader.domain.usecase.SyncRemoteBooksUseCase
import com.pluk.reader.domain.usecase.UploadBooksUseCase
import java.io.File
import kotlinx.coroutines.CompletableDeferred

/**
 * La nube y la biblioteca local en una sola clase falsa, para probar la sincronización completa.
 * Registra en [calls] lo que ocurre, en orden.
 */
class FakeSyncBackend : BookUploadRepository, BookFileStore, RemoteLibrary, QuotaSource, CloudBooksRepository {
    val calls = mutableListOf<String>()

    /** Sin conexión: toda llamada a la nube falla con [RemoteUnavailableException]. */
    var offline = false

    /** Se corta la conexión después de esta cantidad de descargas. */
    var dropAfterDownloads: Int? = null

    /** Si no es null, [listBooks] espera a que se complete antes de responder. */
    var listGate: CompletableDeferred<Unit>? = null

    var remoteBooks: List<RemoteBook> = emptyList()
    var pending = mutableListOf<PendingUpload>()
    var cloudOnly = mutableListOf<String>()
    var failingDownloads = mutableSetOf<String>()
    var quota = StorageQuota(usedBytes = 0, quotaBytes = 100L * 1024 * 1024)

    val downloaded = mutableSetOf<String>()
    val uploaded = mutableListOf<String>()
    var listings = 0

    private var downloadsDone = 0

    fun unavailable() = RemoteUnavailableException("sin red")

    fun useCase() = SyncLibraryUseCase(
        SyncRemoteBooksUseCase(this, this),
        UploadBooksUseCase(this, this, this, this),
        SyncCoversUseCase(this, this),
        DownloadBookUseCase(this, this),
        this,
    )

    fun pendingBook(id: String, sizeBytes: Long = 1) =
        PendingUpload(RemoteBook(id, "T$id", emptyList(), sizeBytes), File("/libros/$id.epub"))

    // RemoteLibrary
    override suspend fun listBooks(): Result<List<RemoteBook>> {
        listings++
        calls += "listar"
        listGate?.await()
        return if (offline) Result.failure(unavailable()) else Result.success(remoteBooks)
    }

    override suspend fun saveBook(book: RemoteBook): Result<Unit> =
        if (offline) Result.failure(unavailable()) else Result.success(Unit)

    // CloudBooksRepository
    override suspend fun addCloudOnly(books: List<RemoteBook>) {
        books.map { it.id }.filter { it !in downloaded && it !in cloudOnly }.forEach { cloudOnly += it }
    }

    override suspend fun cloudOnlyBookIds(): List<String> = cloudOnly.filter { it !in downloaded }
    override suspend fun isDownloaded(bookId: String) = bookId in downloaded
    override fun newTempFile(): File = File.createTempFile("sync", ".tmp").also { it.deleteOnExit() }
    override suspend fun coversToUpload(): List<CoverFile> = emptyList()
    override suspend fun markCoverUploaded(bookId: String) = Unit
    override suspend fun cloudBooksWithoutCover(): List<String> = emptyList()
    override suspend fun installCover(bookId: String, downloaded: File) = Unit
    override suspend fun install(bookId: String, downloaded: File): Result<Unit> {
        downloaded.delete()
        this.downloaded += bookId
        return Result.success(Unit)
    }

    // BookUploadRepository
    override suspend fun pending(): List<PendingUpload> = pending.toList()
    override suspend fun markUploaded(bookId: String, sizeBytes: Long, coverUploaded: Boolean) {
        pending.removeAll { it.book.id == bookId }
    }

    // BookFileStore
    override suspend fun upload(bookId: String, file: File): Result<Unit> {
        if (offline) return Result.failure(unavailable())
        calls += "subir:$bookId"
        uploaded += bookId
        return Result.success(Unit)
    }

    override suspend fun download(bookId: String, destination: File): Result<Unit> {
        if (offline) return Result.failure(unavailable())
        if (dropAfterDownloads?.let { downloadsDone >= it } == true) return Result.failure(unavailable())
        if (bookId in failingDownloads) return Result.failure(IllegalStateException("corrupto"))
        downloadsDone++
        calls += "bajar:$bookId"
        return Result.success(Unit)
    }

    override suspend fun uploadCover(bookId: String, file: File): Result<Unit> = Result.success(Unit)
    override suspend fun downloadCover(bookId: String, destination: File): Result<Boolean> = Result.success(false)

    // QuotaSource
    override suspend fun current(): Result<StorageQuota> =
        if (offline) Result.failure(unavailable()) else Result.success(quota)
}
