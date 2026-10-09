package com.pluk.reader.domain.usecase

import com.pluk.reader.domain.ApplicationScope
import com.pluk.reader.domain.account.AccountRepository
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Qué le impidió a la última pasada dejarlo todo al día (SYN-008). */
sealed interface SyncIssue {
    data object Offline : SyncIssue
    data class NotEnoughSpace(val count: Int) : SyncIssue
    data class Failed(val count: Int) : SyncIssue
}

/** Estado visible de la sincronización (SYN-008): corriendo, y el problema de la última pasada si lo hubo. */
data class SyncState(val running: Boolean = false, val issue: SyncIssue? = null)

fun SyncReport.toIssue(): SyncIssue? = when {
    offline -> SyncIssue.Offline
    failed > 0 -> SyncIssue.Failed(failed)
    notEnoughSpace > 0 -> SyncIssue.NotEnoughSpace(notEnoughSpace)
    else -> null
}

/**
 * Sincroniza la biblioteca sola (SYN-001): al arrancar, al iniciar sesión, cada vez que alguien lo pide
 * (por ejemplo tras importar) y al volver a la app si pasó más de [ttlMs] desde la última pasada exitosa.
 * Vive mientras viva la aplicación, no la pantalla. Nunca corren dos pasadas a la vez: un pedido durante una
 * pasada hace que corra una más al terminar, así un libro importado a mitad de camino no queda esperando.
 * Sin sesión no hace nada.
 *
 * Hay dos tipos de pedido. [request] es explícito (importar, reintentar, sesión nueva) y siempre corre.
 * [requestIfStale] corre solo si la última pasada exitosa es vieja: una pasada sin conexión o con error no
 * cuenta, así que el próximo regreso a la app reintenta.
 */
@Singleton
class LibrarySync internal constructor(
    private val sync: SyncLibraryUseCase,
    private val account: AccountRepository,
    private val scope: CoroutineScope,
    private val now: () -> Long,
    private val ttlMs: Long,
) {
    @Inject
    constructor(
        sync: SyncLibraryUseCase,
        account: AccountRepository,
        @ApplicationScope scope: CoroutineScope,
    ) : this(sync, account, scope, ::monotonicMillis, DEFAULT_TTL_MS)

    private val requests = Channel<Unit>(Channel.CONFLATED)

    /** Hay un pedido explícito sin atender. Separado del canal para que un pedido por antigüedad no lo pise. */
    private val explicitRequest = AtomicBoolean(false)

    /** Cuándo terminó la última pasada exitosa, o null si no hubo o la última falló. */
    @Volatile
    private var lastSuccessAt: Long? = null
    private val _state = MutableStateFlow(SyncState())
    val state: StateFlow<SyncState> = _state

    private var started = false

    /** Empieza a escuchar la sesión y los pedidos. Llamarlo otra vez no hace nada. */
    @Synchronized
    fun start() {
        if (started) return
        started = true
        scope.launch {
            // La sesión llega de forma asíncrona: cada usuario que aparece dispara una pasada.
            account.user.filterNotNull().distinctUntilChangedBy { it.id }.collect { request() }
        }
        scope.launch {
            requests.receiveAsFlow().collect {
                val explicit = explicitRequest.getAndSet(false)
                if ((explicit || isStale()) && account.user.first() != null) runOnce()
            }
        }
    }

    /** Pide una pasada que siempre corre. Varios pedidos seguidos se juntan en uno. */
    fun request() {
        explicitRequest.set(true)
        requests.trySend(Unit)
    }

    /** Pide una pasada solo si la última exitosa es más vieja que el TTL. Pensado para cuando la app vuelve a primer plano. */
    fun requestIfStale() {
        requests.trySend(Unit)
    }

    private fun isStale(): Boolean = lastSuccessAt?.let { now() - it >= ttlMs } ?: true

    private suspend fun runOnce() {
        _state.update { it.copy(running = true) }
        var succeeded = false
        val issue = try {
            val report = sync()
            succeeded = !report.offline
            report.toIssue()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            SyncIssue.Failed(1)
        }
        lastSuccessAt = if (succeeded) now() else null
        _state.value = SyncState(running = false, issue = issue)
    }

    companion object {
        /** Cada cuánto, como máximo, se repite la pasada al volver a la app. */
        const val DEFAULT_TTL_MS = 5 * 60 * 1000L

        private fun monotonicMillis() = System.nanoTime() / 1_000_000
    }
}
