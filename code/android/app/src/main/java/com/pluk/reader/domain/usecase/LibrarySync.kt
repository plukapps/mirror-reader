package com.pluk.reader.domain.usecase

import com.pluk.reader.domain.ApplicationScope
import com.pluk.reader.domain.account.AccountRepository
import com.pluk.reader.domain.sync.SyncGate
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Qué le impidió a la última pasada dejarlo todo al día (SYN-008). */
sealed interface SyncIssue {
    data object Offline : SyncIssue

    /** "Sincronizar solo con Wi-Fi" y sin Wi-Fi: no es un error, se espera (PRF-023). */
    data object WaitingForWifi : SyncIssue
    data class NotEnoughSpace(val count: Int) : SyncIssue
    data class Failed(val count: Int) : SyncIssue
}

/** Permite hacer algo sin pasadas de sincronización de la biblioteca en curso (PRF-009). */
interface LibraryPassGate {
    suspend fun <T> whileIdle(block: suspend () -> T): T
}

/** Estado visible de la sincronización (SYN-008): corriendo, y el problema de la última pasada si lo hubo. */
data class SyncState(val running: Boolean = false, val issue: SyncIssue? = null)

fun SyncReport.toIssue(): SyncIssue? = when {
    waitingForWifi -> SyncIssue.WaitingForWifi
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
 *
 * El momento de la última pasada exitosa vive solo en memoria, a propósito: un proceso nuevo (la app no corría o
 * Android la mató) siempre sincroniza al arrancar, y mientras el proceso siga vivo el TTL evita repetirla de más.
 * No hay sincronización con la app cerrada.
 */
@Singleton
class LibrarySync internal constructor(
    private val sync: SyncLibraryUseCase,
    private val account: AccountRepository,
    private val scope: CoroutineScope,
    private val now: () -> Long = ::monotonicMillis,
    private val ttlMs: Long = DEFAULT_TTL_MS,
    private val gate: SyncGate = SyncGate.ALWAYS_OPEN,
) : LibrarySyncRequester, LibraryPassGate {
    @Inject
    constructor(
        sync: SyncLibraryUseCase,
        account: AccountRepository,
        @ApplicationScope scope: CoroutineScope,
        gate: SyncGate,
    ) : this(sync, account, scope, ::monotonicMillis, DEFAULT_TTL_MS, gate)

    private val requests = Channel<Unit>(Channel.CONFLATED)

    /** Hay un pedido explícito sin atender. Separado del canal para que un pedido por antigüedad no lo pise. */
    private val explicitRequest = AtomicBoolean(false)

    /** Cuándo terminó la última pasada exitosa, o null si no hubo o la última falló. */
    @Volatile
    private var lastSuccessAt: Long? = null

    /**
     * Usuario de la última pasada que empezó. Un pedido sin pasada previa para ese usuario corre aunque el TTL no
     * haya vencido (cambio de cuenta); y si ya corrió una para él, el aviso de sesión no suma otra al arrancar.
     */
    @Volatile
    private var lastRunUserId: String? = null
    private val _state = MutableStateFlow(SyncState())
    val state: StateFlow<SyncState> = _state

    private var started = false

    /** Lo toma cada pasada; [whileIdle] lo toma para que no haya pasadas mientras corre. */
    private val passLock = Mutex()

    /** Empieza a escuchar la sesión y los pedidos. Llamarlo otra vez no hace nada. */
    @Synchronized
    fun start() {
        if (started) return
        started = true
        scope.launch {
            // La sesión llega de forma asíncrona: cada usuario que aparece dispara una pasada.
            // El consumidor decide si hace falta una pasada para ese usuario (ver abajo): así no hay carrera con onStart.
            // Sin filtrar antes los null: así salir y volver a entrar con la misma cuenta cuenta como sesión nueva (PRF-009).
            account.user.distinctUntilChangedBy { it?.id }.filterNotNull().collect { requests.trySend(Unit) }
        }
        scope.launch {
            // PRF-023: mientras falte el Wi-Fi, la biblioteca lo dice; al volver, el aviso se va (la pasada la pide
            // SyncGateWatcher con la app a la vista, o la próxima vuelta a la app).
            gate.open.collect { open ->
                _state.update {
                    when {
                        !open -> it.copy(issue = SyncIssue.WaitingForWifi)
                        it.issue == SyncIssue.WaitingForWifi -> it.copy(issue = null)
                        else -> it
                    }
                }
            }
        }
        scope.launch {
            requests.receiveAsFlow().collect {
                val explicit = explicitRequest.getAndSet(false)
                val user = account.user.first()
                // Un usuario para el que aún no corrió ninguna pasada siempre sincroniza (inicio de sesión, cambio de cuenta).
                if (user != null && (explicit || isStale() || user.id != lastRunUserId)) runOnce(user.id)
            }
        }
    }

    /** Pide una pasada que siempre corre. Varios pedidos seguidos se juntan en uno. */
    override fun request() {
        explicitRequest.set(true)
        requests.trySend(Unit)
    }

    /** Pide una pasada solo si la última exitosa es más vieja que el TTL. Pensado para cuando la app vuelve a primer plano. */
    fun requestIfStale() {
        requests.trySend(Unit)
    }

    private fun isStale(): Boolean = lastSuccessAt?.let { now() - it >= ttlMs } ?: true

    /**
     * PRF-009: corre [block] sin ninguna pasada en curso ni nueva (espera a que termine la que esté corriendo) y
     * olvida la última pasada, así la próxima sesión, aunque sea de la misma cuenta, baja la biblioteca de nuevo.
     */
    override suspend fun <T> whileIdle(block: suspend () -> T): T = passLock.withLock {
        lastRunUserId = null
        lastSuccessAt = null
        block()
    }

    private suspend fun runOnce(userId: String) = passLock.withLock {
        // La sesión pudo cerrarse mientras la pasada esperaba (PRF-009): no se sincroniza una cuenta que ya salió.
        if (account.user.first()?.id != userId) return@withLock
        lastRunUserId = userId
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
