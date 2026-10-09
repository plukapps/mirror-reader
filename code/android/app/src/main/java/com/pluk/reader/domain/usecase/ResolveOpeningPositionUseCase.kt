package com.pluk.reader.domain.usecase

import com.pluk.reader.domain.PositionMerge
import com.pluk.reader.domain.account.AccountRepository
import com.pluk.reader.domain.exceedsJumpThreshold
import com.pluk.reader.domain.mergePosition
import com.pluk.reader.domain.model.ReadingPosition
import com.pluk.reader.domain.model.RemotePosition
import com.pluk.reader.domain.remote.RemotePositions
import com.pluk.reader.domain.repository.PositionRepository
import com.pluk.reader.domain.repository.PositionSyncStore
import javax.inject.Inject
import kotlin.math.abs
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/** Qué hacer con la posición al abrir un libro (SYN-003, SYN-012). */
sealed interface OpeningPosition {
    /** Se abre donde está la local (o no hay otra). */
    data object Local : OpeningPosition

    /** Había una lectura más reciente de otro dispositivo y la diferencia es chica: ya se guardó aquí. */
    data object Updated : OpeningPosition

    /** Hay una lectura más reciente y la diferencia es grande: hay que preguntar al usuario. */
    data class Confirm(val remote: RemotePosition, val local: ReadingPosition) : OpeningPosition
}

/**
 * Al abrir un libro consulta su posición más reciente en la nube antes de mostrarlo (SYN-012). Gana la lectura
 * más reciente (SYN-003): si la diferencia con la local es de hasta el 2 % del libro se usa sin
 * preguntar; si es mayor, se pregunta. Sin conexión, sin sesión o sin respuesta en [FETCH_TIMEOUT_MS] se abre con la
 * local: leer nunca se bloquea.
 *
 * Con la app a la vista, [PositionSync] suele haber aplicado la posición antes (SYN-012); esta consulta cubre el
 * resto: app recién abierta, listener sin conexión, primera apertura en un dispositivo nuevo.
 */
class ResolveOpeningPositionUseCase @Inject constructor(
    private val remote: RemotePositions,
    private val store: PositionSyncStore,
    private val positions: PositionRepository,
    private val account: AccountRepository,
) {
    suspend operator fun invoke(bookId: String): OpeningPosition {
        if (account.user.first() == null) return OpeningPosition.Local
        val incoming = withTimeoutOrNull(FETCH_TIMEOUT_MS) { remote.fetch(bookId) }?.getOrNull() ?: return OpeningPosition.Local
        val local = store.local(bookId)
        return when (mergePosition(local, incoming)) {
            PositionMerge.UseRemote ->
                if (local == null || !isBigJump(local.position, incoming.position)) {
                    store.applyRemote(incoming.position)
                    OpeningPosition.Updated
                } else {
                    OpeningPosition.Confirm(incoming, local.position)
                }
            PositionMerge.MarkSynced -> {
                store.markSynced(bookId, incoming.position.readAt)
                OpeningPosition.Local
            }
            PositionMerge.PushLocal, PositionMerge.Nothing -> OpeningPosition.Local
        }
    }

    /** El usuario eligió continuar desde la lectura del otro dispositivo. */
    suspend fun accept(remote: RemotePosition) = store.applyRemote(remote.position)

    /**
     * El usuario eligió quedarse en su posición. Se vuelve a guardar para que pase a ser la lectura más reciente y
     * la nube no la pise con la del otro dispositivo.
     */
    suspend fun decline(local: ReadingPosition) = positions.save(local.bookId, local.locatorJson, local.progress)

    // Sin progresión en alguna de las dos no se puede medir la diferencia: se pregunta.
    private fun isBigJump(local: ReadingPosition, remote: ReadingPosition): Boolean {
        val a = local.progress ?: return true
        val b = remote.progress ?: return true
        return exceedsJumpThreshold(abs(b - a))
    }

    companion object {
        /** Cuánto se espera la respuesta de la nube al abrir un libro. */
        const val FETCH_TIMEOUT_MS = 2_000L
    }
}
