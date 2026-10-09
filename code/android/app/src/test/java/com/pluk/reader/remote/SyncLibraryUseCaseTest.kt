package com.pluk.reader.remote

import com.pluk.reader.domain.account.StorageQuota
import com.pluk.reader.domain.remote.RemoteBook
import com.pluk.reader.domain.usecase.SyncIssue
import com.pluk.reader.domain.usecase.SyncReport
import com.pluk.reader.domain.usecase.toIssue
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** SYN-001, SYN-008, LIB-007, LIB-009: una pasada deja la biblioteca igual en la nube y en el dispositivo. */
class SyncLibraryUseCaseTest {
    private val backend = FakeSyncBackend()

    // SYN-001: lo importado se sube y lo que falta se baja, sin que el usuario pulse nada
    @Test
    fun uploadsPendingBooksAndDownloadsMissingOnes() = runTest {
        backend.pending += backend.pendingBook("p1")
        backend.remoteBooks = listOf(RemoteBook("n1", "N1", emptyList(), 1), RemoteBook("n2", "N2", emptyList(), 1))

        val report = backend.useCase()()

        assertEquals(1, report.upload.uploaded)
        assertEquals(2, report.downloaded)
        assertEquals(setOf("n1", "n2"), backend.downloaded)
        assertNull(report.toIssue())
        // Primero se lee la nube, después se sube y al final se baja
        assertEquals(listOf("listar", "subir:p1", "bajar:n1", "bajar:n2"), backend.calls)
    }

    // LIB-007: un libro que ya está en el dispositivo no se baja otra vez
    @Test
    fun doesNotDownloadWhatIsAlreadyOnTheDevice() = runTest {
        backend.downloaded += "a"
        backend.cloudOnly += "a"
        backend.cloudOnly += "b"

        val report = backend.useCase()()

        assertEquals(1, report.downloaded)
        assertEquals(listOf("listar", "bajar:b"), backend.calls)
    }

    // SYN-001: repetir la pasada no vuelve a subir ni a bajar nada
    @Test
    fun aSecondPassHasNothingLeftToDo() = runTest {
        backend.pending += backend.pendingBook("p1")
        backend.remoteBooks = listOf(RemoteBook("n1", "N1", emptyList(), 1))
        backend.useCase()()
        backend.calls.clear()

        val second = backend.useCase()()

        assertEquals(SyncReport(), second)
        assertEquals(listOf("listar"), backend.calls)
    }

    // SYN-001, SYN-008: sin conexión no se pierde nada y se informa
    @Test
    fun offlineLeavesEverythingPendingAndReportsIt() = runTest {
        backend.offline = true
        backend.pending += backend.pendingBook("p1")
        backend.cloudOnly += "n1"

        val report = backend.useCase()()

        assertTrue(report.offline)
        assertEquals(SyncIssue.Offline, report.toIssue())
        assertEquals(listOf("p1"), backend.pending.map { it.book.id })
        assertTrue(backend.downloaded.isEmpty())
    }

    // SYN-001: si la nube se alcanza al listar pero la conexión se corta bajando, lo bajado queda y se informa
    @Test
    fun connectionLostWhileDownloadingKeepsWhatWasDownloaded() = runTest {
        backend.cloudOnly += listOf("a", "b", "c")
        backend.dropAfterDownloads = 1

        val report = backend.useCase()()

        assertEquals(1, report.downloaded)
        assertTrue(report.offline)
        assertEquals(setOf("a"), backend.downloaded)
    }

    // Un libro que falla no frena a los demás
    @Test
    fun aFailedDownloadDoesNotStopTheOthers() = runTest {
        backend.cloudOnly += listOf("a", "b", "c")
        backend.failingDownloads += "b"

        val report = backend.useCase()()

        assertEquals(2, report.downloaded)
        assertEquals(1, report.downloadFailed)
        assertFalse(report.offline)
        assertEquals(SyncIssue.Failed(1), report.toIssue())
        assertEquals(setOf("a", "c"), backend.downloaded)
    }

    // LIB-009: lo que no entra en la cuota se avisa, queda pendiente y no corta la bajada
    @Test
    fun booksOverTheQuotaStayPendingAndTheRestSyncs() = runTest {
        backend.quota = StorageQuota(usedBytes = 90, quotaBytes = 100)
        backend.pending += backend.pendingBook("grande", sizeBytes = 50)
        backend.pending += backend.pendingBook("chico", sizeBytes = 5)
        backend.cloudOnly += "n1"

        val report = backend.useCase()()

        assertEquals(1, report.upload.uploaded)
        assertEquals(1, report.notEnoughSpace)
        assertEquals(SyncIssue.NotEnoughSpace(1), report.toIssue())
        assertEquals(listOf("grande"), backend.pending.map { it.book.id })
        assertEquals(1, report.downloaded)
    }

    @Test
    fun issuesArePrioritizedOfflineThenFailedThenNoSpace() {
        assertNull(SyncReport().toIssue())
        val noSpace = SyncReport(upload = com.pluk.reader.domain.usecase.UploadReport(notEnoughSpace = 2))
        assertEquals(SyncIssue.NotEnoughSpace(2), noSpace.toIssue())
        assertEquals(SyncIssue.Failed(1), noSpace.copy(downloadFailed = 1).toIssue())
        assertEquals(SyncIssue.Offline, noSpace.copy(downloadFailed = 1, unreachable = true).toIssue())
    }

    // SYN-011: al terminar una pasada normal salen las posiciones de lectura que esperaban a que el libro estuviera en la nube
    @Test
    fun aCompletedPassSendsThePendingReadingPositionsLast() = runTest {
        backend.pending += backend.pendingBook("p1")

        backend.useCase()()

        assertEquals(1, backend.positionFlushes)
    }

    @Test
    fun anOfflinePassDoesNotTryToSendPositions() = runTest {
        backend.offline = true
        backend.useCase()()
        assertEquals(0, backend.positionFlushes)
    }
}
