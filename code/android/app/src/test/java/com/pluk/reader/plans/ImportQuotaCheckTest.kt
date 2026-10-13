package com.pluk.reader.plans

import com.pluk.reader.domain.account.StorageQuota
import com.pluk.reader.domain.model.ImportOutcome
import com.pluk.reader.domain.plans.ImportQuotaCheck
import com.pluk.reader.domain.plans.PlanPrompt
import com.pluk.reader.domain.plans.PlanPrompts
import com.pluk.reader.domain.plans.PlanId
import com.pluk.reader.domain.plans.QuotaBlock
import com.pluk.reader.domain.plans.quotaBlock
import com.pluk.reader.domain.remote.RemoteBook
import com.pluk.reader.domain.repository.BookUploadRepository
import com.pluk.reader.domain.repository.PendingUpload
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

private const val MIB = 1024L * 1024

/** Libros pendientes de subir en memoria, para los tests de planes. */
class FakePendingUploads(vararg books: Pair<String, Long>) : BookUploadRepository {
    var items = books.map { (id, size) -> PendingUpload(RemoteBook(id, "T$id", emptyList(), size), File("/$id.epub")) }
    override suspend fun pending() = items
    override suspend fun markUploaded(bookId: String, sizeBytes: Long, coverUploaded: Boolean) = Unit
}

class ImportQuotaCheckTest {
    private val quota = StorageQuota(12 * MIB, 15 * MIB)

    // PLN-009: sin cuota conocida no hay hoja de espacio
    @Test
    fun noQuotaNoBlock() {
        assertNull(quotaBlock(null, 0, 1 * MIB))
    }

    // PLN-014: justo el máximo de Gratis entra; un byte más es archivo muy grande, antes que el espacio
    @Test
    fun maxFileSizeIsCheckedFirst() {
        assertNull(quotaBlock(quota, 0, 2 * MIB))
        assertEquals(QuotaBlock.TooLarge(2 * MIB + 1, 2 * MIB, PlanId.FREE), quotaBlock(quota, 0, 2 * MIB + 1))
        assertEquals(QuotaBlock.TooLarge(3 * MIB, 2 * MIB, PlanId.FREE), quotaBlock(StorageQuota(15 * MIB, 15 * MIB), 0, 3 * MIB))
    }

    // PLN-014: sin cuota o con un plan desconocido vale Gratis; en Lector el máximo es 50 MB
    @Test
    fun planComesFromTheQuota() {
        assertEquals(QuotaBlock.TooLarge(3 * MIB, 2 * MIB, PlanId.FREE), quotaBlock(null, 0, 3 * MIB))
        assertEquals(QuotaBlock.TooLarge(3 * MIB, 2 * MIB, PlanId.FREE), quotaBlock(StorageQuota(0, 15 * MIB, plan = "gold"), 0, 3 * MIB))
        val reader = StorageQuota(0, 5L * 1024 * MIB, plan = "reader")
        assertNull(quotaBlock(reader, 0, 50 * MIB))
        assertEquals(QuotaBlock.TooLarge(51 * MIB, 50 * MIB, PlanId.READER), quotaBlock(reader, 0, 51 * MIB))
    }

    // PLN-008: justo entra
    @Test
    fun exactFitIsFine() {
        assertNull(quotaBlock(StorageQuota(13 * MIB, 15 * MIB), 0, 2 * MIB))
    }

    // PLN-008: queda espacio pero no entra
    @Test
    fun biggerThanFreeDoesNotFit() {
        assertEquals(QuotaBlock.DoesNotFit(2 * MIB, 1 * MIB), quotaBlock(StorageQuota(14 * MIB, 15 * MIB), 0, 2 * MIB))
    }

    // PLN-007: lleno
    @Test
    fun fullQuotaBlocksAnyBook() {
        assertEquals(QuotaBlock.Full, quotaBlock(StorageQuota(15 * MIB, 15 * MIB), 0, 1))
    }

    // PLN-009: los pendientes de subir descuentan espacio
    @Test
    fun pendingBooksCount() {
        assertEquals(QuotaBlock.DoesNotFit(2 * MIB, 1 * MIB), quotaBlock(quota, 2 * MIB, 2 * MIB))
        assertEquals(QuotaBlock.Full, quotaBlock(quota, 3 * MIB, 1))
    }

    private fun check(cache: FakeQuotaCache, uploads: FakePendingUploads, prompts: PlanPrompts) =
        ImportQuotaCheck(cache, uploads, prompts)

    // PLN-008, PLN-009: el libro importado no cuenta como pendiente propio; los otros pendientes sí
    @Test
    fun showsDoesNotFitForTheImportedBook() = runTest {
        val prompts = PlanPrompts()
        val uploads = FakePendingUploads("viejo" to 2 * MIB, "nuevo" to 2 * MIB)
        check(FakeQuotaCache(quota), uploads, prompts)(listOf(ImportOutcome.Imported("nuevo", "Dune")))
        assertEquals(PlanPrompt.Quota("Dune", QuotaBlock.DoesNotFit(2 * MIB, 1 * MIB), 15 * MIB), prompts.current.value)
    }

    // PLN-014: sin cuota leída, un archivo más grande que el máximo de Gratis también muestra la hoja
    @Test
    fun tooLargeWithoutQuotaUsesFree() = runTest {
        val prompts = PlanPrompts()
        check(FakeQuotaCache(), FakePendingUploads("a" to 3 * MIB), prompts)(listOf(ImportOutcome.Imported("a", "A")))
        assertEquals(PlanPrompt.Quota("A", QuotaBlock.TooLarge(3 * MIB, 2 * MIB, PlanId.FREE), null), prompts.current.value)
    }

    // PLN-009: varios a la vez, una sola hoja con el primero que no entra
    @Test
    fun severalBooksShowOneSheetForTheFirstThatDoesNotFit() = runTest {
        val prompts = PlanPrompts()
        val uploads = FakePendingUploads("a" to 2 * MIB, "b" to 2 * MIB, "c" to 1 * MIB)
        check(FakeQuotaCache(quota), uploads, prompts)(
            listOf(
                ImportOutcome.Imported("a", "A"),
                ImportOutcome.AlreadyInLibrary("x", "X"),
                ImportOutcome.Imported("b", "B"),
                ImportOutcome.Imported("c", "C"),
            ),
        )
        assertEquals(PlanPrompt.Quota("B", QuotaBlock.DoesNotFit(2 * MIB, 1 * MIB), 15 * MIB), prompts.current.value)
    }

    // PLN-009: sin cuota leída, nada
    @Test
    fun withoutCachedQuotaNothingIsShown() = runTest {
        val prompts = PlanPrompts()
        check(FakeQuotaCache(), FakePendingUploads("a" to 1 * MIB), prompts)(listOf(ImportOutcome.Imported("a", "A")))
        assertNull(prompts.current.value)
    }

    @Test
    fun whenEverythingFitsNothingIsShown() = runTest {
        val prompts = PlanPrompts()
        check(FakeQuotaCache(quota), FakePendingUploads("a" to 1 * MIB), prompts)(listOf(ImportOutcome.Imported("a", "A")))
        assertNull(prompts.current.value)
    }

    // PLN-009: "Ahora no" cierra la hoja
    @Test
    fun dismissClosesThePrompt() = runTest {
        val prompts = PlanPrompts()
        check(FakeQuotaCache(StorageQuota(15 * MIB, 15 * MIB)), FakePendingUploads("a" to MIB), prompts)(listOf(ImportOutcome.Imported("a", "A")))
        assertEquals(QuotaBlock.Full, (prompts.current.value as PlanPrompt.Quota).block)
        prompts.dismiss()
        assertNull(prompts.current.value)
    }
}
