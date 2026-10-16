package com.pluk.reader.domain.account

import com.pluk.reader.domain.repository.LibraryRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged

/** GST-010: el aviso "solo en este dispositivo" se ve sin sesión, con al menos un libro, si no se descartó. */
fun shouldShowGuestNotice(signedIn: Boolean, hasBooks: Boolean, dismissed: Boolean): Boolean = !signedIn && hasBooks && !dismissed

/** GST-010: el aviso se descarta una vez por dispositivo, en Inicio o en la Biblioteca. Se borra al cerrar sesión (ADR 0019). */
interface GuestNoticePreference {
    val dismissed: Flow<Boolean>

    suspend fun dismiss()
}

/** GST-023: las dos opciones del banner de Inicio. GST-025 elige cuál. */
enum class GuestBannerVariant {
    /** A: "Tus libros, en todos tus dispositivos". */
    AllDevices,

    /** B: "Tus libros sin respaldo". */
    NoBackup,
}

/** GST-010: un solo aviso para Inicio y la Biblioteca. */
class GuestNotice @Inject constructor(
    account: AccountRepository,
    library: LibraryRepository,
    private val preference: GuestNoticePreference,
) {
    val visible: Flow<Boolean> = combine(account.user, library.books, preference.dismissed) { user, books, dismissed ->
        shouldShowGuestNotice(user != null, books.isNotEmpty(), dismissed)
    }.distinctUntilChanged()

    /** Cerrarlo o tocar "Crear cuenta" lo descarta para siempre en este dispositivo. */
    suspend fun dismiss() = preference.dismiss()
}
