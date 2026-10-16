package com.pluk.reader.domain.account

import com.pluk.reader.domain.repository.LibraryRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged

/** GST-010: la cruz esconde el aviso por 7 días; después vuelve. */
const val GUEST_NOTICE_SNOOZE_MS = 7L * 24 * 60 * 60 * 1000

/**
 * GST-010: el aviso "solo en este dispositivo" se ve sin sesión, con al menos un libro, salvo en los 7 días siguientes
 * a cerrarlo ([dismissedAt], o null si nunca se cerró).
 */
fun shouldShowGuestNotice(signedIn: Boolean, hasBooks: Boolean, dismissedAt: Long?, now: Long): Boolean =
    !signedIn && hasBooks && (dismissedAt == null || now - dismissedAt >= GUEST_NOTICE_SNOOZE_MS)

/** GST-010: cuándo se cerró el aviso en este dispositivo, en Inicio o en la Biblioteca. Se borra al cerrar sesión (ADR 0019). */
interface GuestNoticePreference {
    val dismissedAt: Flow<Long?>

    suspend fun dismiss(at: Long)
}

/** GST-023: las dos opciones del banner de Inicio. GST-025 elige cuál. */
enum class GuestBannerVariant {
    /** A: "Tus libros, en todos tus dispositivos". */
    AllDevices,

    /** B: "Tus libros sin respaldo". */
    NoBackup,
}

/** GST-010: un solo aviso para Inicio y la Biblioteca. */
class GuestNotice(
    account: AccountRepository,
    library: LibraryRepository,
    private val preference: GuestNoticePreference,
    private val now: () -> Long,
) {
    @Inject
    constructor(account: AccountRepository, library: LibraryRepository, preference: GuestNoticePreference) :
        this(account, library, preference, System::currentTimeMillis)

    /** Se calcula cada vez que se abre una pantalla con el aviso: pasados los 7 días, vuelve. */
    val visible: Flow<Boolean> = combine(account.user, library.books, preference.dismissedAt) { user, books, dismissedAt ->
        shouldShowGuestNotice(user != null, books.isNotEmpty(), dismissedAt, now())
    }.distinctUntilChanged()

    /** La cruz lo esconde por 7 días en las dos pantallas. "Crear cuenta" no lo esconde. */
    suspend fun dismiss() = preference.dismiss(now())
}
