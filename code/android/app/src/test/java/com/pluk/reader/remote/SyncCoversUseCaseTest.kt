package com.pluk.reader.remote

import com.pluk.reader.domain.remote.BookFileStore
import com.pluk.reader.domain.remote.RemoteBook
import com.pluk.reader.domain.remote.RemoteUnavailableException
import com.pluk.reader.domain.repository.CloudBooksRepository
import com.pluk.reader.domain.repository.CoverFile
import com.pluk.reader.domain.usecase.CoverSyncReport
import com.pluk.reader.domain.usecase.SyncCoversUseCase
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** LIB-012: la portada de un libro se ve en todos los dispositivos, también antes de descargarlo. */
class SyncCoversUseCaseTest {
    @get:Rule val folder = TemporaryFolder()

    private val calls = mutableListOf<String>()

    private inner class FakeCloud(
        withCover: List<String> = emptyList(),
        val withoutCover: List<String> = emptyList(),
    ) : CloudBooksRepository {
        /** Portadas locales cuya subida aún no está anotada; `markCoverUploaded` las saca, como la base de datos. */
        val withCover = withCover.toMutableList()
        val marked = mutableListOf<String>()
        val installed = mutableListOf<String>()
        val temps = mutableListOf<File>()
        override suspend fun cloudOnlyBookIds(): List<String> = emptyList()
        override suspend fun coversToUpload() = withCover.map { CoverFile(it, File("/portadas/$it.jpg")) }
        override suspend fun markCoverUploaded(bookId: String) {
            withCover.remove(bookId)
            marked += bookId
        }
        override suspend fun cloudBooksWithoutCover() = withoutCover
        override suspend fun installCover(bookId: String, downloaded: File) {
            installed += bookId
            downloaded.delete()
        }
        override fun newTempFile(): File = folder.newFile().also { temps += it }
        override suspend fun addCloudOnly(books: List<RemoteBook>) = Unit
        override suspend fun isDownloaded(bookId: String) = true
        override suspend fun install(bookId: String, downloaded: File) = Result.success(Unit)
    }

    private inner class FakeFiles(
        val uploadResults: Map<String, Result<Unit>> = emptyMap(),
        val downloadResults: Map<String, Result<Boolean>> = emptyMap(),
    ) : BookFileStore {
        override suspend fun upload(bookId: String, file: File) = Result.success(Unit)
        override suspend fun download(bookId: String, destination: File) = Result.success(Unit)
        override suspend fun uploadCover(bookId: String, file: File): Result<Unit> {
            calls += "subir:$bookId"
            return uploadResults[bookId] ?: Result.success(Unit)
        }
        override suspend fun downloadCover(bookId: String, destination: File): Result<Boolean> {
            calls += "bajar:$bookId"
            return downloadResults[bookId] ?: Result.success(true)
        }
    }

    @Test
    fun uploadsTheLocalCoversOfUploadedBooks() = runTest {
        val report = SyncCoversUseCase(FakeCloud(withCover = listOf("a", "b")), FakeFiles())()
        assertEquals(CoverSyncReport(uploaded = 2), report)
        assertEquals(listOf("subir:a", "subir:b"), calls)
    }

    // Una portada ya anotada como subida no se vuelve a consultar en las sincronizaciones siguientes
    @Test
    fun aCoverIsUploadedOnlyOnceAcrossRuns() = runTest {
        val cloud = FakeCloud(withCover = listOf("a", "b"))
        val useCase = SyncCoversUseCase(cloud, FakeFiles())
        useCase()
        calls.clear()

        val second = useCase()

        assertEquals(listOf("a", "b"), cloud.marked)
        assertEquals(CoverSyncReport(), second)
        assertEquals(emptyList<String>(), calls)
    }

    // Si la subida falla no se anota: se reintenta en la próxima pasada
    @Test
    fun aFailedCoverIsNotMarkedAndIsRetried() = runTest {
        val cloud = FakeCloud(withCover = listOf("a"))
        val failing = FakeFiles(uploadResults = mapOf("a" to Result.failure(IllegalStateException("denegado"))))
        SyncCoversUseCase(cloud, failing)()
        assertEquals(emptyList<String>(), cloud.marked)

        calls.clear()
        SyncCoversUseCase(cloud, FakeFiles())()
        assertEquals(listOf("subir:a"), calls)
        assertEquals(listOf("a"), cloud.marked)
    }

    @Test
    fun downloadsTheCoverOfCloudOnlyBooksAndInstallsIt() = runTest {
        val cloud = FakeCloud(withoutCover = listOf("a"))
        val report = SyncCoversUseCase(cloud, FakeFiles())()
        assertEquals(CoverSyncReport(downloaded = 1), report)
        assertEquals(listOf("a"), cloud.installed)
    }

    // Un libro sin portada en la nube no es un error: no se instala nada y no queda el temporal
    @Test
    fun aBookWithoutCoverInTheCloudInstallsNothing() = runTest {
        val cloud = FakeCloud(withoutCover = listOf("a"))
        val report = SyncCoversUseCase(cloud, FakeFiles(downloadResults = mapOf("a" to Result.success(false))))()
        assertEquals(CoverSyncReport(), report)
        assertEquals(emptyList<String>(), cloud.installed)
        assertFalse(cloud.temps.single().exists())
    }

    // Un fallo en una portada no corta las demás
    @Test
    fun aFailureOnOneCoverDoesNotStopTheOthers() = runTest {
        val cloud = FakeCloud(withCover = listOf("a", "b"), withoutCover = listOf("c", "d"))
        val files = FakeFiles(
            uploadResults = mapOf("a" to Result.failure(IllegalStateException("denegado"))),
            downloadResults = mapOf("c" to Result.failure(IllegalStateException("boom"))),
        )
        val report = SyncCoversUseCase(cloud, files)()
        assertEquals(CoverSyncReport(uploaded = 1, downloaded = 1), report)
        assertEquals(listOf("d"), cloud.installed)
        assertFalse(cloud.temps.any { it.exists() })
    }

    // SYN-008: sin conexión se corta la pasada
    @Test
    fun noConnectionStopsTheRun() = runTest {
        val cloud = FakeCloud(withCover = listOf("a", "b"), withoutCover = listOf("c"))
        val files = FakeFiles(uploadResults = mapOf("a" to Result.failure(RemoteUnavailableException())))
        val report = SyncCoversUseCase(cloud, files)()
        assertEquals(CoverSyncReport(), report)
        assertEquals(listOf("subir:a"), calls)
    }

    @Test
    fun noConnectionWhileDownloadingStopsTheRun() = runTest {
        val cloud = FakeCloud(withoutCover = listOf("a", "b"))
        val files = FakeFiles(downloadResults = mapOf("a" to Result.failure(RemoteUnavailableException())))
        SyncCoversUseCase(cloud, files)()
        assertEquals(listOf("bajar:a"), calls)
        assertEquals(emptyList<String>(), cloud.installed)
    }
}
