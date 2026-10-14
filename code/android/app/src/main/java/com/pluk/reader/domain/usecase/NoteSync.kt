package com.pluk.reader.domain.usecase

import com.pluk.reader.domain.ApplicationScope
import com.pluk.reader.domain.account.AccountRepository
import com.pluk.reader.domain.flags.Feature
import com.pluk.reader.domain.flags.FeatureFlags
import com.pluk.reader.domain.remote.NoteSyncCursor
import com.pluk.reader.domain.remote.RemoteNoteChanges
import com.pluk.reader.domain.remote.RemoteNotes
import com.pluk.reader.domain.remote.RemoteUnavailableException
import com.pluk.reader.domain.repository.NoteSyncStore
import com.pluk.reader.domain.sync.SyncGate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Sube las notas pendientes (SYN-016). Lo usan [SyncLibraryUseCase] al terminar y el lector al guardar. */
fun interface NoteFlusher {
    suspend fun flush()
}

/** Pide subir las notas sin esperar el resultado. */
fun interface NoteFlushRequester {
    fun requestFlush()
}

/**
 * Sincroniza las notas entre dispositivos (SYN-016, SYN-005, SYN-007), solo si la función está prendida (SYN-017).
 *
 * - Subir: las pendientes, al guardar una nota, al terminar una pasada de [LibrarySync] y al prenderse la función.
 * - Bajar: un listener mientras la app está a la vista, desde la última vez. Gana la edición más reciente; lo decide
 *   [NoteSyncStore.applyRemote].
 * - Apagada: no se sube, no se baja y no se borra nada de la nube. Las notas siguen en el dispositivo.
 *
 * Vive mientras viva la aplicación. Sin sesión no hace nada.
 */
@Singleton
class NoteSync @Inject constructor(
    private val remote: RemoteNotes,
    private val store: NoteSyncStore,
    private val cursor: NoteSyncCursor,
    private val flags: FeatureFlags,
    private val account: AccountRepository,
    @ApplicationScope private val scope: CoroutineScope,
    private val gate: SyncGate,
) : NoteFlusher, NoteFlushRequester, CloudListener {
    private val flushLock = Mutex()
    private var listenJob: Job? = null

    private val enabled get() = flags.isEnabled(Feature.NotesSync)

    override suspend fun flush() {
        flushLock.withLock {
            if (!enabled || account.user.first() == null || !gate.isOpen()) return
            for (note in store.pending()) {
                val result = remote.push(note)
                if (result.isSuccess) {
                    store.markSynced(note.note.id, note.editedAt)
                } else if (result.exceptionOrNull() is RemoteUnavailableException) {
                    return
                }
                // Otro rechazo (por ejemplo el libro todavía no llegó a la nube): queda pendiente y se reintenta.
            }
            store.purgeSyncedDeletions()
        }
    }

    override fun requestFlush() {
        scope.launch { flush() }
    }

    @Synchronized
    override fun startListening() {
        if (listenJob?.isActive == true) return
        listenJob = scope.launch {
            account.user.distinctUntilChangedBy { it?.id }.collectLatest { user ->
                if (user == null) return@collectLatest
                // SYN-017, PRF-023: solo con la función prendida y la red permitida. Al prenderse, sube lo pendiente.
                combine(flags.state(Feature.NotesSync), gate.open) { on, open -> on && open }
                    .distinctUntilChanged()
                    .collectLatest { active ->
                        if (active) {
                            flush()
                            listen()
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

    private suspend fun listen() {
        remote.observeChanges(cursor.lastSeenAt()).collect { result -> result.onSuccess { apply(it) } }
    }

    private suspend fun apply(changes: RemoteNoteChanges) {
        changes.notes.forEach { store.applyRemote(it) }
        changes.newestUpdatedAt?.let { newest ->
            if (newest > (cursor.lastSeenAt() ?: 0L)) cursor.setLastSeenAt(newest)
        }
    }
}
