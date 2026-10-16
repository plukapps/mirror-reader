package com.pluk.reader.domain.account

import com.pluk.reader.domain.flags.FeatureFlags
import com.pluk.reader.domain.flags.Setting
import com.pluk.reader.domain.repository.LibraryRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged

private const val DAY_MS = 24L * 60 * 60 * 1000

/**
 * GST-010, GST-026: el aviso "solo en este dispositivo" se ve sin sesión, con al menos un libro, salvo en los
 * [snoozeDays] días siguientes a cerrarlo ([dismissedAt], o null si nunca se cerró). Días negativos valen 0.
 */
fun shouldShowGuestNotice(signedIn: Boolean, hasBooks: Boolean, dismissedAt: Long?, now: Long, snoozeDays: Long): Boolean =
    !signedIn && hasBooks && (dismissedAt == null || now - dismissedAt >= snoozeDays.coerceAtLeast(0) * DAY_MS)

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
    flags: FeatureFlags,
    private val now: () -> Long,
) {
    @Inject
    constructor(account: AccountRepository, library: LibraryRepository, preference: GuestNoticePreference, flags: FeatureFlags) :
        this(account, library, preference, flags, System::currentTimeMillis)

    /** Se calcula cada vez que se abre una pantalla con el aviso: pasados los días (GST-026), vuelve. */
    val visible: Flow<Boolean> = combine(
        account.user,
        library.books,
        preference.dismissedAt,
        flags.setting(Setting.GuestNoticeSnoozeDays),
    ) { user, books, dismissedAt, snoozeDays ->
        shouldShowGuestNotice(user != null, books.isNotEmpty(), dismissedAt, now(), snoozeDays)
    }.distinctUntilChanged()

    /** La cruz lo esconde por unos días (GST-026) en las dos pantallas. "Crear cuenta" no lo esconde. */
    suspend fun dismiss() = preference.dismiss(now())
}
