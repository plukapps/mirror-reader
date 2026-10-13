package com.pluk.reader.domain.usecase

import com.pluk.reader.domain.account.AuthRepository
import com.pluk.reader.domain.account.LocalAccountData
import com.pluk.reader.domain.onboarding.ReminderScheduler
import com.pluk.reader.domain.repository.BookRemovals
import com.pluk.reader.domain.repository.BookUploadRepository
import com.pluk.reader.domain.repository.PositionSyncStore
import javax.inject.Inject
import kotlinx.coroutines.CancellationException

/**
 * PRF-009: cierra la sesión y borra de este dispositivo los datos de la cuenta (siguen en la nube).
 *
 * El orden importa: primero se dejan de escuchar los cambios de la nube y se espera a que no haya ninguna pasada
 * de sincronización en curso; después se borra y recién al final se cierra la sesión. Así ninguna escucha ni pasada
 * vuelve a escribir datos de la cuenta después del borrado. Si el borrado falla, igual se cierra la sesión: al
 * volver a entrar, la sincronización reconstruye la biblioteca desde la nube. Al terminar, las escuchas se vuelven
 * a encender: sin sesión no hacen nada y arrancan solas con la próxima cuenta.
 */
class SignOut internal constructor(
    private val listeners: List<CloudListener>,
    private val gate: LibraryPassGate,
    private val localData: LocalAccountData,
    private val auth: AuthRepository,
    private val reminders: ReminderScheduler,
    private val hasPending: suspend () -> Boolean,
) {
    @Inject
    constructor(
        positionSync: PositionSync,
        cloud: CloudListener,
        librarySync: LibrarySync,
        localData: LocalAccountData,
        auth: AuthRepository,
        reminders: ReminderScheduler,
        uploads: BookUploadRepository,
        positions: PositionSyncStore,
        removals: BookRemovals,
    ) : this(
        listeners = listOf(positionSync, cloud),
        gate = librarySync,
        localData = localData,
        auth = auth,
        reminders = reminders,
        hasPending = {
            uploads.pending().isNotEmpty() || positions.pending().isNotEmpty() || removals.pendingCloudRemovals().isNotEmpty()
        },
    )

    /** Hay libros importados sin subir, progreso o libros quitados que todavía no llegaron a la nube. */
    suspend fun hasPendingChanges(): Boolean = hasPending()

    suspend operator fun invoke(): Result<Unit> {
        listeners.forEach { it.stopListening() }
        val result = gate.whileIdle {
            try {
                localData.wipe()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Se sigue igual: quedarse con la sesión abierta sería peor que quedar con datos viejos que la
                // próxima sincronización reemplaza.
            }
            reminders.cancel()
            auth.signOut()
        }
        // La app sigue a la vista: sin sesión las escuchas no hacen nada, y arrancan solas cuando alguien entra.
        listeners.forEach { it.startListening() }
        return result
    }
}
