package com.pluk.reader.remote

import com.pluk.reader.domain.account.QuotaSource
import com.pluk.reader.domain.account.StorageQuota
import com.pluk.reader.domain.remote.BookFileStore
import com.pluk.reader.domain.remote.QuotaExceededException
import com.pluk.reader.domain.remote.RemoteBook
import com.pluk.reader.domain.remote.RemoteLibrary
import com.pluk.reader.domain.remote.RemoteUnavailableException
import com.pluk.reader.domain.repository.BookUploadRepository
import com.pluk.reader.domain.repository.PendingUpload
import com.pluk.reader.domain.usecase.UploadBooksUseCase
import com.pluk.reader.domain.usecase.UploadReport
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private const val MIB = 1024L * 1024L

/** SYN-001 (local primero, se envía cuando hay conexión), LIB-009 (la cuota bloquea subidas, nunca la lectura). */
class UploadBooksUseCaseTest {
    private val calls = mutableListOf<String>()

    private fun pending(id: String, sizeBytes: Long, withCover: Boolean = false) =
        PendingUpload(
            RemoteBook(id, "T$id", listOf("A"), sizeBytes),
            File("/libros/$id.epub"),
            cover = File("/portadas/$id.jpg").takeIf { withCover },
        )

    private inner class FakeUploads(val items: List<PendingUpload>) : BookUploadRepository {
        val marked = mutableListOf<Pair<String, Long>>()
        override suspend fun pending() = items
        override suspend fun markUploaded(bookId: String, sizeBytes: Long) {
            calls += "marcar:$bookId"
            marked += bookId to sizeBytes
        }
    }

    private inner class FakeFiles(val results: Map<String, Result<Unit>> = emptyMap()) : BookFileStore {
        override suspend fun upload(bookId: String, file: File): Result<Unit> {
            calls += "archivo:$bookId"
            return results[bookId] ?: Result.success(Unit)
        }
        override suspend fun download(bookId: String, destination: File) = Result.success(Unit)
        var coverResult: Result<Unit> = Result.success(Unit)
        override suspend fun uploadCover(bookId: String, file: File): Result<Unit> {
            calls += "portada:$bookId"
            return coverResult
        }
        override suspend fun downloadCover(bookId: String, destination: File) = Result.success(false)
    }

    private inner class FakeRemote(val failFor: Map<String, Throwable> = emptyMap()) : RemoteLibrary {
        override suspend fun listBooks() = Result.success(emptyList<RemoteBook>())
        override suspend fun saveBook(book: RemoteBook): Result<Unit> {
            calls += "metadatos:${book.id}"
            return failFor[book.id]?.let { Result.failure(it) } ?: Result.success(Unit)
        }
    }

    private class FakeQuota(private val result: Result<StorageQuota>) : QuotaSource {
        override suspend fun current() = result
    }

    private fun quota(used: Long = 0, limit: Long = 15 * MIB) = FakeQuota(Result.success(StorageQuota(used, limit)))

    private fun useCase(
        uploads: FakeUploads,
        files: FakeFiles = FakeFiles(),
        remote: FakeRemote = FakeRemote(),
        quota: QuotaSource = quota(),
    ) = UploadBooksUseCase(uploads, files, remote, quota)

    // SYN-001: el archivo va primero (la regla valida tipo, tamaño y cuota) y los metadatos después
    @Test
    fun uploadsTheFileThenTheMetadataThenMarksTheBook() = runTest {
        val uploads = FakeUploads(listOf(pending("a", 1 * MIB)))
        val report = useCase(uploads).invoke()
        assertEquals(UploadReport(uploaded = 1), report)
        assertEquals(listOf("archivo:a", "metadatos:a", "marcar:a"), calls)
        assertEquals(listOf("a" to 1 * MIB), uploads.marked)
    }

    // LIB-012: la portada sube después de los metadatos y antes de marcar el libro como subido
    @Test
    fun uploadsTheCoverBeforeMarkingTheBook() = runTest {
        val uploads = FakeUploads(listOf(pending("a", MIB, withCover = true)))
        val report = useCase(uploads).invoke()
        assertEquals(UploadReport(uploaded = 1), report)
        assertEquals(listOf("archivo:a", "metadatos:a", "portada:a", "marcar:a"), calls)
    }

    @Test
    fun aBookWithoutCoverSkipsTheCoverStep() = runTest {
        useCase(FakeUploads(listOf(pending("a", MIB)))).invoke()
        assertTrue("portada:a" !in calls)
    }

    // LIB-012: la portada es secundaria; un rechazo no impide que el libro quede subido
    @Test
    fun aCoverRejectionDoesNotStopTheBook() = runTest {
        val uploads = FakeUploads(listOf(pending("a", MIB, withCover = true)))
        val files = FakeFiles().apply { coverResult = Result.failure(IllegalStateException("denegado")) }
        val report = useCase(uploads, files).invoke()
        assertEquals(UploadReport(uploaded = 1), report)
        assertEquals(listOf("a" to MIB), uploads.marked)
    }

    // SYN-001: sin conexión al subir la portada el libro queda pendiente y se reintenta entero (es seguro)
    @Test
    fun noConnectionWhileUploadingTheCoverKeepsTheBookPending() = runTest {
        val uploads = FakeUploads(listOf(pending("a", MIB, withCover = true)))
        val files = FakeFiles().apply { coverResult = Result.failure(RemoteUnavailableException()) }
        val report = useCase(uploads, files).invoke()
        assertTrue(report.unreachable)
        assertEquals(emptyList<Pair<String, Long>>(), uploads.marked)
    }

    @Test
    fun nothingPendingDoesNothing() = runTest {
        val report = useCase(FakeUploads(emptyList())).invoke()
        assertEquals(UploadReport(), report)
        assertTrue(report.nothingToDo)
        assertEquals(emptyList<String>(), calls)
    }

    // SYN-001: un fallo deja el libro pendiente y no frena a los demás
    @Test
    fun aFailedFileUploadLeavesTheBookPendingAndContinues() = runTest {
        val uploads = FakeUploads(listOf(pending("a", MIB), pending("b", MIB)))
        val files = FakeFiles(mapOf("a" to Result.failure(IllegalStateException("boom"))))
        val report = useCase(uploads, files).invoke()
        assertEquals(UploadReport(uploaded = 1, failed = 1), report)
        assertEquals(listOf("b" to MIB), uploads.marked)
        assertTrue("metadatos de a no se escriben sin su archivo", "metadatos:a" !in calls)
    }

    @Test
    fun aFailedMetadataWriteLeavesTheBookPending() = runTest {
        val uploads = FakeUploads(listOf(pending("a", MIB), pending("b", MIB)))
        val remote = FakeRemote(mapOf("a" to IllegalStateException("boom")))
        val report = useCase(uploads, remote = remote).invoke()
        assertEquals(UploadReport(uploaded = 1, failed = 1), report)
        assertEquals(listOf("b" to MIB), uploads.marked)
    }

    // SYN-001, SYN-008: sin conexión se corta la tanda y todo queda pendiente
    @Test
    fun noConnectionStopsTheRunAndKeepsEverythingPending() = runTest {
        val uploads = FakeUploads(listOf(pending("a", MIB), pending("b", MIB)))
        val files = FakeFiles(mapOf("a" to Result.failure(RemoteUnavailableException("sin red"))))
        val report = useCase(uploads, files).invoke()
        assertEquals(UploadReport(unreachable = true), report)
        assertEquals(emptyList<Pair<String, Long>>(), uploads.marked)
        assertEquals(listOf("archivo:a"), calls)
    }

    @Test
    fun noConnectionWhileWritingMetadataAlsoStopsTheRun() = runTest {
        val uploads = FakeUploads(listOf(pending("a", MIB), pending("b", MIB)))
        val remote = FakeRemote(mapOf("a" to RemoteUnavailableException("sin red")))
        val report = useCase(uploads, remote = remote).invoke()
        assertTrue(report.unreachable)
        assertEquals(emptyList<Pair<String, Long>>(), uploads.marked)
        assertTrue("archivo:b" !in calls)
    }

    @Test
    fun ifTheQuotaCannotBeReadNothingIsUploaded() = runTest {
        val uploads = FakeUploads(listOf(pending("a", MIB)))
        val report = useCase(uploads, quota = FakeQuota(Result.failure(RemoteUnavailableException()))).invoke()
        assertEquals(UploadReport(unreachable = true), report)
        assertEquals(emptyList<String>(), calls)
    }

    // LIB-009: lo que no entra en el espacio libre no se sube, pero lo que sí entra sí
    @Test
    fun booksThatDoNotFitAreSkippedAndSmallerOnesStillGo() = runTest {
        val uploads = FakeUploads(listOf(pending("a", 3 * MIB), pending("b", 3 * MIB), pending("c", 1 * MIB)))
        val report = useCase(uploads, quota = quota(used = 10 * MIB, limit = 15 * MIB)).invoke()
        assertEquals(UploadReport(uploaded = 2, notEnoughSpace = 1), report)
        assertEquals(listOf("a", "c"), uploads.marked.map { it.first })
        assertTrue("archivo:b" !in calls)
    }

    // LIB-009: el espacio libre baja con cada subida de la tanda, aunque el servidor aún no lo refleje
    @Test
    fun spaceConsumedInThisRunCountsAgainstTheNextBooks() = runTest {
        val uploads = FakeUploads(listOf(pending("a", 4 * MIB), pending("b", 4 * MIB)))
        val report = useCase(uploads, quota = quota(used = 0, limit = 6 * MIB)).invoke()
        assertEquals(UploadReport(uploaded = 1, notEnoughSpace = 1), report)
    }

    @Test
    fun aServerSideQuotaRejectionCountsAsNoSpaceAndContinues() = runTest {
        val uploads = FakeUploads(listOf(pending("a", MIB), pending("b", MIB)))
        val files = FakeFiles(mapOf("a" to Result.failure(QuotaExceededException())))
        val report = useCase(uploads, files).invoke()
        assertEquals(UploadReport(uploaded = 1, notEnoughSpace = 1), report)
    }
}
