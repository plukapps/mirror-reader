package com.pluk.reader.domain.usecase

import com.pluk.reader.domain.ApplicationScope
import com.pluk.reader.domain.account.AccountRepository
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
 * Sincroniza la biblioteca sola (SYN-001): al arrancar, al iniciar sesión y cada vez que alguien lo pide
 * (por ejemplo tras importar). Vive mientras viva la aplicación, no la pantalla. Nunca corren dos pasadas a la
 * vez: un pedido durante una pasada hace que corra una más al terminar, así un libro importado a mitad de camino
 * no queda esperando. Sin sesión no hace nada.
 */
@Singleton
class LibrarySync @Inject constructor(
    private val sync: SyncLibraryUseCase,
    private val account: AccountRepository,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val requests = Channel<Unit>(Channel.CONFLATED)
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
            requests.receiveAsFlow().collect { if (account.user.first() != null) runOnce() }
        }
    }

    /** Pide una pasada. Varios pedidos seguidos se juntan en uno. */
    fun request() {
        requests.trySend(Unit)
    }

    private suspend fun runOnce() {
        _state.update { it.copy(running = true) }
        val issue = try {
            sync().toIssue()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            SyncIssue.Failed(1)
        }
        _state.value = SyncState(running = false, issue = issue)
    }
}
