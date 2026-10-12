package com.pluk.reader.domain.usecase

import com.pluk.reader.domain.ApplicationScope
import com.pluk.reader.domain.account.AccountRepository
import com.pluk.reader.domain.remote.RemoteBookChanges
import com.pluk.reader.domain.repository.KnownBooks
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
) : CloudListener {
    @Inject
    constructor(
        remote: RemoteBookChanges,
        known: KnownBooks,
        sync: LibrarySyncRequester,
        account: AccountRepository,
        @ApplicationScope scope: CoroutineScope,
    ) : this(remote, known, sync, account, scope, System::currentTimeMillis)

    private var listenJob: Job? = null

    @Synchronized
    override fun startListening() {
        if (listenJob?.isActive == true) return
        listenJob = scope.launch {
            account.user.distinctUntilChangedBy { it?.id }.collectLatest { user ->
                if (user == null) return@collectLatest
                remote.observeAliveSince(now() - LOOKBACK_MS).collect { result ->
                    result.onSuccess { ids -> if (ids.any { it !in known.knownBookIds() }) sync.request() }
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

/** Las escuchas de la nube que siguen a la app a la vista: borrados (LIB-020) y libros nuevos (SYN-015). */
@Singleton
class CloudListeners @Inject constructor(
    private val removals: RemovalSync,
    private val library: LibraryWatcher,
) : CloudListener {
    override fun startListening() {
        removals.startListening()
        library.startListening()
    }

    override fun stopListening() {
        removals.stopListening()
        library.stopListening()
    }
}
