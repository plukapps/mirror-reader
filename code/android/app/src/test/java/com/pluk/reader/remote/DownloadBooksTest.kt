package com.pluk.reader.remote

import com.pluk.reader.domain.model.BookOpenException
import com.pluk.reader.domain.remote.BookFileStore
import com.pluk.reader.domain.remote.RemoteBook
import com.pluk.reader.domain.remote.RemoteLibrary
import com.pluk.reader.domain.remote.RemoteUnavailableException
import com.pluk.reader.domain.repository.CloudBooksRepository
import com.pluk.reader.domain.repository.CoverFile
import com.pluk.reader.domain.usecase.DownloadBookUseCase
import com.pluk.reader.domain.usecase.SyncRemoteBooksUseCase
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** LIB-007: los libros de la nube aparecen como "solo en la nube" y se descargan una sola vez, al abrirlos. */
class DownloadBooksTest {
    @get:Rule val folder = TemporaryFolder()

    private val calls = mutableListOf<String>()

    private inner class FakeCloud(
        val downloaded: MutableSet<String> = mutableSetOf(),
        val installResult: Result<Unit> = Result.success(Unit),
    ) : CloudBooksRepository {
        val added = mutableListOf<RemoteBook>()
        lateinit var temp: File
        override suspend fun addCloudOnly(books: List<RemoteBook>) {
            calls += "agregar:${books.map { it.id }}"
            added += books
        }
        override suspend fun cloudOnlyBookIds(): List<String> = emptyList()
        override suspend fun uploadedBooksWithCover(): List<CoverFile> = emptyList()
        override suspend fun cloudBooksWithoutCover(): List<String> = emptyList()
        override suspend fun installCover(bookId: String, downloaded: File) = Unit
        override suspend fun isDownloaded(bookId: String) = bookId in downloaded
        override fun newTempFile(): File = folder.newFile().also { temp = it }
        override suspend fun install(bookId: String, downloaded: File): Result<Unit> {
            calls += "instalar:$bookId"
            if (installResult.isSuccess) this.downloaded += bookId
            return installResult
        }
    }

    private inner class FakeFiles(val result: Result<Unit> = Result.success(Unit)) : BookFileStore {
        override suspend fun upload(bookId: String, file: File) = Result.success(Unit)
        override suspend fun uploadCover(bookId: String, file: File) = Result.success(Unit)
        override suspend fun downloadCover(bookId: String, destination: File) = Result.success(false)
        override suspend fun download(bookId: String, destination: File): Result<Unit> {
            calls += "bajar:$bookId"
            return result
        }
    }

    private class FakeRemote(val result: Result<List<RemoteBook>>) : RemoteLibrary {
        override suspend fun listBooks() = result
        override suspend fun saveBook(book: RemoteBook) = Result.success(Unit)
    }

    private fun remoteBook(id: String) = RemoteBook(id, "T$id", listOf("A"), 100)

    @Test
    fun syncAddsTheRemoteBooksAsCloudOnly() = runTest {
        val cloud = FakeCloud()
        val result = SyncRemoteBooksUseCase(FakeRemote(Result.success(listOf(remoteBook("a"), remoteBook("b")))), cloud)()
        assertTrue(result.isSuccess)
        assertEquals(listOf("a", "b"), cloud.added.map { it.id })
    }

    @Test
    fun syncWithoutConnectionChangesNothing() = runTest {
        val cloud = FakeCloud()
        val result = SyncRemoteBooksUseCase(FakeRemote(Result.failure(RemoteUnavailableException())), cloud)()
        assertTrue(result.isFailure)
        assertTrue(cloud.added.isEmpty())
    }

    @Test
    fun downloadsABookThatIsOnlyInTheCloudAndInstallsIt() = runTest {
        val cloud = FakeCloud()
        val result = DownloadBookUseCase(cloud, FakeFiles())("a")
        assertTrue(result.isSuccess)
        assertEquals(listOf("bajar:a", "instalar:a"), calls)
        assertTrue(cloud.isDownloaded("a"))
    }

    // Lo que ya está en el dispositivo no se vuelve a bajar
    @Test
    fun doesNotDownloadWhatIsAlreadyOnTheDevice() = runTest {
        val cloud = FakeCloud(downloaded = mutableSetOf("a"))
        val result = DownloadBookUseCase(cloud, FakeFiles())("a")
        assertTrue(result.isSuccess)
        assertTrue(calls.isEmpty())
    }

    // Una descarga que falla no instala nada ni deja el temporal
    @Test
    fun failedDownloadInstallsNothingAndRemovesTheTempFile() = runTest {
        val cloud = FakeCloud()
        val result = DownloadBookUseCase(cloud, FakeFiles(Result.failure(RemoteUnavailableException())))("a")
        assertEquals("Sin conexión. No se pudo descargar el libro.", (result.exceptionOrNull() as BookOpenException).message)
        assertEquals(listOf("bajar:a"), calls)
        assertFalse(cloud.temp.exists())
        assertFalse(cloud.isDownloaded("a"))
    }

    @Test
    fun unexpectedDownloadErrorGivesAGenericMessage() = runTest {
        val result = DownloadBookUseCase(FakeCloud(), FakeFiles(Result.failure(IllegalStateException("x"))))("a")
        assertEquals("No se pudo descargar el libro.", result.exceptionOrNull()?.message)
    }

    // Si el archivo bajado no es válido, el fallo de la instalación llega tal cual
    @Test
    fun installFailureIsReported() = runTest {
        val cloud = FakeCloud(installResult = Result.failure(BookOpenException("Archivo dañado.")))
        val result = DownloadBookUseCase(cloud, FakeFiles())("a")
        assertEquals("Archivo dañado.", result.exceptionOrNull()?.message)
        assertFalse(cloud.isDownloaded("a"))
    }
}
