package com.pluk.reader.domain.plans

import com.pluk.reader.domain.account.StorageQuota
import com.pluk.reader.domain.model.ImportOutcome
import com.pluk.reader.domain.profile.QuotaCache
import com.pluk.reader.domain.repository.BookUploadRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Por qué un libro importado no entra en la nube (PLN-007, PLN-008). */
sealed interface QuotaBlock {
    /** El espacio ya está lleno (P02a). */
    data object Full : QuotaBlock

    /** Queda espacio, pero este libro no entra (PLN-008). */
    data class DoesNotFit(val bookBytes: Long, val freeBytes: Long) : QuotaBlock

    /** El archivo supera el máximo del plan [plan] (PLN-014, P02b). */
    data class TooLarge(val bookBytes: Long, val maxBytes: Long, val plan: PlanId) : QuotaBlock
}

/**
 * PLN-014, ACC-010: primero el tamaño máximo por archivo (el del servidor si se conoce; sin cuota, el de Free). PLN-009: después, con la última
 * cuota conocida, descontando [pendingBytes] de los libros de este dispositivo que esperan subirse, ¿entra un libro
 * de [bookBytes]? Null si entra, o si nunca se leyó la cuota (vale el aviso de LIB-009).
 */
fun quotaBlock(quota: StorageQuota?, pendingBytes: Long, bookBytes: Long): QuotaBlock? {
    val plan = PlanCatalog.planOf(quota)
    val maxBytes = PlanCatalog.maxFileBytes(quota)
    if (bookBytes > maxBytes) return QuotaBlock.TooLarge(bookBytes, maxBytes, plan)
    if (quota == null) return null
    val available = quota.freeBytes - pendingBytes
    return when {
        available <= 0 -> QuotaBlock.Full
        bookBytes > available -> QuotaBlock.DoesNotFit(bookBytes, available)
        else -> null
    }
}

/** Funciones pagas con su hoja (P03). Exportar notas (P03a) llega con las anotaciones. */
enum class PaidFeature {
    /** PLN-015: temas y fuentes, de Plus. */
    THEMES_FONTS,
}

/** Hoja de planes a mostrar encima de todo (P02, P03). */
sealed interface PlanPrompt {
    /**
     * PLN-007, PLN-008, PLN-014: [title] se importó pero no entra en la nube. [quotaBytes]: la cuota de la cuenta, o
     * null si nunca se leyó (solo en [QuotaBlock.TooLarge]).
     */
    data class Quota(val title: String, val block: QuotaBlock, val quotaBytes: Long?) : PlanPrompt

    /** PLN-015: una función de un plan pago. */
    data class Paid(val feature: PaidFeature) : PlanPrompt
}

/** La hoja de planes a la vista, compartida entre las pantallas y la raíz que la dibuja (una a la vez). */
@Singleton
class PlanPrompts @Inject constructor() {
    private val _current = MutableStateFlow<PlanPrompt?>(null)
    val current: StateFlow<PlanPrompt?> = _current.asStateFlow()

    fun show(prompt: PlanPrompt) {
        _current.value = prompt
    }

    /** "Ahora no", "Ver planes" o deslizar la hoja: el libro queda en el dispositivo y se reintenta (PLN-009). */
    fun dismiss() {
        _current.value = null
    }
}

/**
 * PLN-007 a PLN-009: después de importar, comprueba si lo importado entra en la nube y, si no, muestra una sola hoja
 * con el primero que no entra. Cada libro que entra cuenta para los siguientes del mismo lote.
 */
class ImportQuotaCheck @Inject constructor(
    private val quotaCache: QuotaCache,
    private val uploads: BookUploadRepository,
    private val prompts: PlanPrompts,
) {
    suspend operator fun invoke(outcomes: List<ImportOutcome>) {
        val imported = outcomes.filterIsInstance<ImportOutcome.Imported>()
        if (imported.isEmpty()) return
        val quota = quotaCache.last()
        val pending = uploads.pending()
        val importedIds = imported.map { it.bookId }.toSet()
        var pendingBytes = pending.filter { it.book.id !in importedIds }.sumOf { it.book.sizeBytes }
        val sizes = pending.associate { it.book.id to it.book.sizeBytes }
        for (book in imported) {
            val size = sizes[book.bookId] ?: continue
            val block = quotaBlock(quota, pendingBytes, size)
            if (block != null) {
                prompts.show(PlanPrompt.Quota(book.title, block, quota?.quotaBytes))
                return
            }
            pendingBytes += size
        }
    }
}
