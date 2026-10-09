package com.pluk.reader.domain.usecase

import com.pluk.reader.domain.ApplicationScope
import com.pluk.reader.domain.PositionMerge
import com.pluk.reader.domain.account.AccountRepository
import com.pluk.reader.domain.mergePosition
import com.pluk.reader.domain.model.RemotePosition
import com.pluk.reader.domain.remote.RemoteChanges
import com.pluk.reader.domain.remote.RemotePositions
import com.pluk.reader.domain.remote.RemoteUnavailableException
import com.pluk.reader.domain.repository.PositionSyncStore
import com.pluk.reader.domain.repository.SyncPreferences
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Envía lo que quedó pendiente de la posición de lectura (SYN-011). Lo usa [SyncLibraryUseCase] al terminar. */
fun interface PositionFlusher {
    suspend fun flush()
}

/**
 * Sincroniza la posición de lectura entre dispositivos (SYN-011, SYN-012, ADR 0011).
 *
 * - Enviar: solo cuando se cierra el libro o la app pasa a segundo plano, no en cada página. Lo que no se pudo
 *   enviar queda pendiente en la base local y sale en el próximo [flush] (al abrir la app o al terminar una
 *   pasada de [LibrarySync]). Nunca corren dos envíos a la vez.
 * - Recibir: un listener en vivo mientras la app está a la vista ([startListening] y [stopListening]) que pide solo
 *   lo cambiado desde la última vez. Gana la lectura más reciente ([mergePosition]).
 *
 * Vive mientras viva la aplicación. Sin sesión no hace nada.
 */
@Singleton
class PositionSync @Inject constructor(
    private val remote: RemotePositions,
    private val store: PositionSyncStore,
    private val prefs: SyncPreferences,
    private val account: AccountRepository,
    @ApplicationScope private val scope: CoroutineScope,
) : PositionFlusher {
    private val flushLock = Mutex()
    private val _remoteUpdates = MutableSharedFlow<RemotePosition>(extraBufferCapacity = UPDATES_BUFFER)
    private var listenJob: Job? = null

    /** Posición más reciente que otro dispositivo escribió y que se guardó aquí (SYN-013). */
    val remoteUpdates: SharedFlow<RemotePosition> = _remoteUpdates.asSharedFlow()

    override suspend fun flush() {
        flushLock.withLock {
            if (account.user.first() == null) return
            val pending = store.pending()
            if (pending.isEmpty()) return
            val deviceId = prefs.deviceId()
            val deviceName = prefs.deviceName()
            for (position in pending) {
                val result = remote.push(position, deviceId, deviceName)
                if (result.isSuccess) {
                    store.markSynced(position.bookId, position.readAt)
                } else if (result.exceptionOrNull() is RemoteUnavailableException) {
                    return
                }
                // Otro fallo (por ejemplo el libro aún no está en la nube): queda pendiente y se reintenta.
            }
        }
    }

    /** Envía lo pendiente sin esperar el resultado. */
    fun requestFlush() {
        scope.launch { flush() }
    }

    /**
     * Se cerró el libro: guarda la última posición ([save]) y envía. Corre en el scope de la aplicación porque
     * la pantalla ya se está yendo.
     */
    fun onBookClosed(save: suspend () -> Unit) {
        scope.launch {
            save()
            flush()
        }
    }

    /** Empieza a recibir cambios mientras la app está a la vista. Llamarlo otra vez no hace nada. */
    @Synchronized
    fun startListening() {
        if (listenJob?.isActive == true) return
        listenJob = scope.launch {
            // La sesión puede llegar después: cada usuario nuevo reinicia la escucha.
            account.user.distinctUntilChangedBy { it?.id }.collectLatest { user ->
                if (user != null) listen()
            }
        }
    }

    /** Deja de recibir cambios: la app dejó de verse. No hay trabajo en segundo plano. */
    @Synchronized
    fun stopListening() {
        listenJob?.cancel()
        listenJob = null
    }

    private suspend fun listen() {
        remote.observeChanges(prefs.lastPositionsSeenAt()).collect { result ->
            result.onSuccess { apply(it) }
        }
    }

    private suspend fun apply(changes: RemoteChanges) {
        val thisDevice = prefs.deviceId()
        var needsFlush = false
        for (incoming in changes.positions) {
            val bookId = incoming.position.bookId
            val local = store.local(bookId)
            when (mergePosition(local, incoming)) {
                PositionMerge.UseRemote -> {
                    store.applyRemote(incoming.position)
                    if (incoming.deviceId != thisDevice) _remoteUpdates.tryEmit(incoming)
                }
                PositionMerge.PushLocal -> {
                    // La local es más nueva. Si estaba marcada como enviada, la nube quedó atrás: se vuelve a enviar.
                    if (local?.isSynced == true) store.markPending(bookId)
                    needsFlush = true
                }
                PositionMerge.MarkSynced -> store.markSynced(bookId, incoming.position.readAt)
                PositionMerge.Nothing -> Unit
            }
        }
        changes.newestUpdatedAt?.let { newest ->
            if (newest > (prefs.lastPositionsSeenAt() ?: 0L)) prefs.setLastPositionsSeenAt(newest)
        }
        if (needsFlush) flush()
    }

    private companion object {
        const val UPDATES_BUFFER = 16
    }
}
