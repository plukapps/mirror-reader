package com.pluk.reader.domain.usecase

import com.pluk.reader.domain.flags.RemoteFeatureFlags
import com.pluk.reader.domain.ApplicationScope
import com.pluk.reader.domain.account.AccountRepository
import com.pluk.reader.domain.remote.RemoteBookChanges
import com.pluk.reader.domain.repository.KnownBooks
import com.pluk.reader.domain.sync.SyncGate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.launch

/** Pide una pasada de la biblioteca, sin el tope de tiempo (SYN-001). */
fun interface LibrarySyncRequester {
    fun request()
}

/**
 * Con la app a la vista, un libro agregado (o vuelto a importar) en otro dispositivo aparece solo (SYN-015): escucha
 * los libros cambiados hace poco y, si alguno no está en este dispositivo, pide una pasada, que lo agrega y lo baja.
 * Solo mira los últimos [LOOKBACK_MS]: cubre la app en segundo plano dentro del tope de la pasada (5 minutos); pasado
 * el tope, la pasada al volver ya lo trae.
 */
@Singleton
class LibraryWatcher internal constructor(
    private val remote: RemoteBookChanges,
    private val known: KnownBooks,
    private val sync: LibrarySyncRequester,
    private val account: AccountRepository,
    private val scope: CoroutineScope,
    private val now: () -> Long,
    private val gate: SyncGate = SyncGate.ALWAYS_OPEN,
) : CloudListener {
    @Inject
    constructor(
        remote: RemoteBookChanges,
        known: KnownBooks,
        sync: LibrarySyncRequester,
        account: AccountRepository,
        @ApplicationScope scope: CoroutineScope,
        gate: SyncGate,
    ) : this(remote, known, sync, account, scope, System::currentTimeMillis, gate)

    private var listenJob: Job? = null

    @Synchronized
    override fun startListening() {
        if (listenJob?.isActive == true) return
        listenJob = scope.launch {
            account.user.distinctUntilChangedBy { it?.id }.collectLatest { user ->
                if (user == null) return@collectLatest
                // PRF-023: sin Wi-Fi no se escuchan libros nuevos.
                gate.open.collectLatest { open ->
                    if (open) {
                        remote.observeAliveSince(now() - LOOKBACK_MS).collect { result ->
                            result.onSuccess { ids -> if (ids.any { it !in known.knownBookIds() }) sync.request() }
                        }
                    }
                }
            }
        }
    }

    @Synchronized
    override fun stopListening() {
        listenJob?.cancel()
        listenJob = null
    }

    companion object {
        const val LOOKBACK_MS = 15 * 60_000L
    }
}

/**
 * PRF-023: con la app a la vista, cuando la puerta se abre (llegó el Wi-Fi o se apagó "Solo con Wi-Fi") pide una
 * pasada y envía las posiciones pendientes. Solo reacciona a cambios: el valor de cuando empieza a escuchar no cuenta.
 */
@Singleton
class SyncGateWatcher @Inject constructor(
    private val gate: SyncGate,
    private val sync: LibrarySyncRequester,
    private val positions: PositionFlushRequester,
    @ApplicationScope private val scope: CoroutineScope,
) : CloudListener {
    private var listenJob: Job? = null

    @Synchronized
    override fun startListening() {
        if (listenJob?.isActive == true) return
        listenJob = scope.launch {
            var wasOpen: Boolean? = null
            gate.open.collect { open ->
                if (open && wasOpen == false) {
                    sync.request()
                    positions.requestFlush()
                }
                wasOpen = open
            }
        }
    }

    @Synchronized
    override fun stopListening() {
        listenJob?.cancel()
        listenJob = null
    }
}

/**
 * Las escuchas de la nube que siguen a la app a la vista: borrados (LIB-020), libros nuevos (SYN-015) y la vuelta
 * del Wi-Fi (PRF-023).
 */
@Singleton
class CloudListeners @Inject constructor(
    private val removals: RemovalSync,
    private val library: LibraryWatcher,
    private val gate: SyncGateWatcher,
    private val flags: RemoteFeatureFlags,
) : CloudListener {
    override fun startListening() {
        // ACC-007, SYN-017: primero los interruptores, que deciden qué más se sincroniza.
        flags.startListening()
        removals.startListening()
        library.startListening()
        gate.startListening()
    }

    override fun stopListening() {
        flags.stopListening()
        removals.stopListening()
        library.stopListening()
        gate.stopListening()
    }
}
