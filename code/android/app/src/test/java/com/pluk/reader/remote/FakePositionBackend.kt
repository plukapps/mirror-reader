package com.pluk.reader.remote

import com.pluk.reader.domain.account.AccountRepository
import com.pluk.reader.domain.account.AccountUser
import com.pluk.reader.domain.model.LocalPosition
import com.pluk.reader.domain.model.ReadingPosition
import com.pluk.reader.domain.model.RemotePosition
import com.pluk.reader.domain.remote.RemoteChanges
import com.pluk.reader.domain.remote.RemotePositions
import com.pluk.reader.domain.remote.RemoteUnavailableException
import com.pluk.reader.domain.repository.PositionSyncStore
import com.pluk.reader.domain.repository.SyncPreferences
import com.pluk.reader.domain.usecase.PositionSync
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow

/** Sesión falsa, con el usuario que se le indique (o ninguno). */
class FakeSession(initial: AccountUser?) : AccountRepository {
    val state = MutableStateFlow(initial)
    override val user = state
    override suspend fun signIn(email: String, password: String) = Result.failure<AccountUser>(IllegalStateException())
}

/**
 * La nube, la base local y las preferencias de la sincronización de posiciones, en una sola clase falsa.
 * [calls] registra lo que pasa, en orden.
 */
class FakePositionBackend : RemotePositions, PositionSyncStore, SyncPreferences {
    val calls = mutableListOf<String>()

    // La nube
    var offline = false
    var rejection: Throwable? = null
    val remote = mutableMapOf<String, RemotePosition>()
    val pushes = mutableListOf<Triple<ReadingPosition, String, String>>()

    /** Cuántas veces se intentó enviar, incluso las que fallaron. */
    var pushAttempts = 0
    var pushGate: CompletableDeferred<Unit>? = null
    val changes = MutableSharedFlow<Result<RemoteChanges>>(extraBufferCapacity = 16)
    val observedSince = mutableListOf<Long?>()
    var fetchResult: Result<RemotePosition?> = Result.success(null)
    var fetchDelayMs = 0L

    // Local
    val local = mutableMapOf<String, LocalPosition>()

    // Preferencias
    var deviceId = "este-dispositivo"
    var seenAt: Long? = null

    fun savedLocally(bookId: String, readAt: Long, synced: Boolean = false, locator: String = "{}") {
        local[bookId] = LocalPosition(ReadingPosition(bookId, locator, 0.5, readAt), synced)
    }

    fun position(bookId: String, readAt: Long, locator: String = "{}") = ReadingPosition(bookId, locator, 0.5, readAt)

    fun remotePosition(bookId: String, readAt: Long, device: String = "otro-dispositivo", locator: String = "{}") =
        RemotePosition(position(bookId, readAt, locator), device, "SM-X510")

    fun sync(account: AccountRepository, scope: CoroutineScope) = PositionSync(this, this, this, account, scope)

    // RemotePositions
    override suspend fun push(position: ReadingPosition, deviceId: String, deviceName: String): Result<Unit> {
        pushAttempts++
        pushGate?.await()
        if (offline) return Result.failure(RemoteUnavailableException("sin red"))
        rejection?.let { return Result.failure(it) }
        calls += "enviar:${position.bookId}"
        pushes += Triple(position, deviceId, deviceName)
        if ((remote[position.bookId]?.position?.readAt ?: 0L) <= position.readAt) {
            remote[position.bookId] = RemotePosition(position, deviceId, deviceName)
        }
        return Result.success(Unit)
    }

    override suspend fun fetch(bookId: String): Result<RemotePosition?> {
        if (fetchDelayMs > 0) kotlinx.coroutines.delay(fetchDelayMs)
        return fetchResult
    }

    override fun observeChanges(sinceMillis: Long?): Flow<Result<RemoteChanges>> {
        observedSince += sinceMillis
        return changes
    }

    // PositionSyncStore
    override suspend fun local(bookId: String): LocalPosition? = local[bookId]

    override suspend fun pending(): List<ReadingPosition> = local.values.filter { !it.isSynced }.map { it.position }

    override suspend fun markSynced(bookId: String, readAt: Long) {
        val current = local[bookId] ?: return
        // Igual que la base de datos: solo si la fila sigue siendo la lectura que se envió.
        if (current.position.readAt == readAt) local[bookId] = current.copy(isSynced = true)
    }

    override suspend fun markPending(bookId: String) {
        local[bookId]?.let { local[bookId] = it.copy(isSynced = false) }
    }

    override suspend fun applyRemote(position: ReadingPosition) {
        val current = local[position.bookId]
        if (current == null || current.position.readAt < position.readAt) local[position.bookId] = LocalPosition(position, true)
    }

    // SyncPreferences
    override suspend fun deviceId(): String = deviceId
    override fun deviceName(): String = "SM-S711B"
    override suspend fun lastPositionsSeenAt(): Long? = seenAt
    override suspend fun setLastPositionsSeenAt(value: Long) {
        seenAt = value
    }
}
