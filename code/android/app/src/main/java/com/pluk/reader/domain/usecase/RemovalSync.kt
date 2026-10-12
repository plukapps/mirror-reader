package com.pluk.reader.domain.usecase

import com.pluk.reader.domain.ApplicationScope
import com.pluk.reader.domain.account.AccountRepository
import com.pluk.reader.domain.remote.RemoteRemovals
import com.pluk.reader.domain.repository.BookRemovals
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Pide enviar los borrados pendientes sin esperar el resultado (LIB-020). */
fun interface RemovalPushRequester {
    fun requestPush()
}

/** Escucha de borrados de otros dispositivos mientras la app se ve (LIB-020). */
interface RemovalListener {
    fun startListening()
    fun stopListening()
}

/** El paso de borrados de la pasada de sincronización. */
fun interface CloudRemovals {
    suspend fun sync(): Result<Unit>
}

/**
 * Borrar un libro de la cuenta (LIB-020, LIB-008). El dispositivo que lo quita ya lo borró de su base; aquí:
 * - se envía a la nube: primero la marca (`deletedAt`), después los archivos; recién entonces queda hecho. Sin
 *   conexión queda pendiente y lo reintenta la próxima pasada.
 * - se aplican los borrados de otros dispositivos: con la app a la vista, al llegar; si no, al sincronizar. Un libro
 *   abierto en el lector se quita al cerrarlo.
 *
 * Vive mientras viva la aplicación. Sin sesión no hace nada.
 */
@Singleton
class RemovalSync @Inject constructor(
    private val remote: RemoteRemovals,
    private val local: BookRemovals,
    private val session: ReadingSession,
    private val account: AccountRepository,
    @ApplicationScope private val scope: CoroutineScope,
) : CloudRemovals, RemovalPushRequester, RemovalListener {
    private val pushLock = Mutex()
    private val applyLock = Mutex()
    private val waitingForClose = mutableSetOf<String>()
    private var listenJob: Job? = null

    init {
        // El estado (y no un evento de cierre) no se pierde aunque el libro se cierre antes de empezar a observar.
        scope.launch {
            session.open.collect { open ->
                val closed = applyLock.withLock {
                    waitingForClose.filter { it !in open }.also { waitingForClose -= it.toSet() }
                }
                if (closed.isNotEmpty()) local.removeDeletedInCloud(closed)
            }
        }
    }

    /** Envía los borrados de aquí y aplica los de la nube. Corta si no hay conexión. */
    override suspend fun sync(): Result<Unit> {
        if (account.user.first() == null) return Result.success(Unit)
        push().onFailure { return Result.failure(it) }
        return remote.deletedBookIds().map { apply(it) }
    }

    override fun requestPush() {
        scope.launch { if (account.user.first() != null) push() }
    }

    private suspend fun push(): Result<Unit> = pushLock.withLock {
        for (bookId in local.pendingCloudRemovals()) {
            remote.markDeleted(bookId).onFailure { return Result.failure(it) }
            remote.deleteFiles(bookId).onFailure { return Result.failure(it) }
            local.markCloudRemoved(bookId)
        }
        Result.success(Unit)
    }

    private suspend fun apply(bookIds: List<String>) {
        val now = applyLock.withLock {
            val open = session.open.value
            val (later, now) = bookIds.partition { it in open }
            waitingForClose += later
            now
        }
        if (now.isNotEmpty()) local.removeDeletedInCloud(now)
    }

    /** Empieza a recibir borrados mientras la app está a la vista. Llamarlo otra vez no hace nada. */
    @Synchronized
    override fun startListening() {
        if (listenJob?.isActive == true) return
        listenJob = scope.launch {
            account.user.distinctUntilChangedBy { it?.id }.collectLatest { user ->
                if (user != null) remote.observeDeleted().collect { result -> result.onSuccess { apply(it) } }
            }
        }
    }

    /** Deja de recibir borrados: la app dejó de verse. */
    @Synchronized
    override fun stopListening() {
        listenJob?.cancel()
        listenJob = null
    }
}
