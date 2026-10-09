package com.pluk.reader.data.remote

import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.Source
import com.pluk.reader.domain.model.ReadingPosition
import com.pluk.reader.domain.model.RemotePosition
import com.pluk.reader.domain.remote.RemoteChanges
import com.pluk.reader.domain.remote.RemotePositions
import com.pluk.reader.domain.remote.RemoteUnavailableException
import java.util.Date
import javax.inject.Inject
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Posiciones en `users/{uid}/positions/{bookId}` (ADR 0011). Las reglas exigen `updatedAt` del servidor y que
 * `readAt` no retroceda (ver `firestore.rules`).
 */
class FirestorePositions @Inject constructor(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth,
) : RemotePositions {
    override suspend fun push(position: ReadingPosition, deviceId: String, deviceName: String): Result<Unit> =
        remoteCall("pushPosition(${position.bookId.take(8)})") {
            val ref = db.document("users/${uid()}/positions/${position.bookId}")
            // Una transacción, no `set`: sin conexión falla enseguida con UNAVAILABLE en lugar de quedar esperando.
            db.runTransaction { tx ->
                val existing = tx.get(ref).takeIf { it.exists() }?.getLong(READ_AT)
                // Hay una lectura más nueva en la nube: no se pisa; llegará por el listener o al abrir el libro.
                if (existing == null || existing <= position.readAt) {
                    tx.set(
                        ref,
                        mapOf(
                            LOCATOR_JSON to position.locatorJson,
                            PROGRESS to position.progress,
                            READ_AT to position.readAt,
                            DEVICE_ID to deviceId,
                            DEVICE_NAME to deviceName,
                            UPDATED_AT to FieldValue.serverTimestamp(),
                        ),
                    )
                }
                null
            }.await()
            Unit
        }

    override suspend fun fetch(bookId: String): Result<RemotePosition?> =
        remoteCall("fetchPosition(${bookId.take(8)})") {
            db.document("users/${uid()}/positions/$bookId").get(Source.SERVER).await().toRemotePosition()
        }

    override fun observeChanges(sinceMillis: Long?): Flow<Result<RemoteChanges>> = callbackFlow {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            trySend(Result.failure(RemoteUnavailableException("No hay sesión iniciada.")))
            close()
            return@callbackFlow
        }
        val collection = db.collection("users/$uid/positions")
        val query: Query = sinceMillis?.let { collection.whereGreaterThan(UPDATED_AT, Timestamp(Date(it))) } ?: collection
        val registration = query.addSnapshotListener { snapshot, error ->
            if (error != null || snapshot == null) {
                trySend(Result.failure(error?.toRemoteError() ?: RemoteUnavailableException("Sin datos de la nube.")))
                close()
                return@addSnapshotListener
            }
            // Las escrituras de este dispositivo que aún no confirmó el servidor llegan con updatedAt vacío: se omiten.
            val docs = snapshot.documentChanges
                .filter { it.type == DocumentChange.Type.ADDED || it.type == DocumentChange.Type.MODIFIED }
                .map { it.document }
                .filter { !it.metadata.hasPendingWrites() }
            if (docs.isNotEmpty()) {
                val newest = docs.mapNotNull { it.getTimestamp(UPDATED_AT)?.toDate()?.time }.maxOrNull()
                trySend(Result.success(RemoteChanges(docs.mapNotNull { it.toRemotePosition() }, newest)))
            }
        }
        awaitClose { registration.remove() }
    }

    private fun uid(): String = auth.currentUser?.uid ?: throw RemoteUnavailableException("No hay sesión iniciada.")

    private fun DocumentSnapshot.toRemotePosition(): RemotePosition? {
        if (!exists()) return null
        val locator = getString(LOCATOR_JSON) ?: return null
        val readAt = getLong(READ_AT) ?: return null
        return RemotePosition(
            ReadingPosition(id, locator, getDouble(PROGRESS), readAt),
            deviceId = getString(DEVICE_ID).orEmpty(),
            deviceName = getString(DEVICE_NAME).orEmpty(),
        )
    }

    private companion object {
        const val LOCATOR_JSON = "locatorJson"
        const val PROGRESS = "progress"
        const val READ_AT = "readAt"
        const val DEVICE_ID = "deviceId"
        const val DEVICE_NAME = "deviceName"
        const val UPDATED_AT = "updatedAt"
    }
}
