package com.pluk.reader.data.remote

import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.pluk.reader.domain.model.Note
import com.pluk.reader.domain.remote.RemoteNoteChanges
import com.pluk.reader.domain.remote.RemoteNotes
import com.pluk.reader.domain.remote.RemoteUnavailableException
import com.pluk.reader.domain.repository.SyncNote
import java.util.Date
import javax.inject.Inject
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Notas en `users/{uid}/notes/{noteId}` (SYN-016). Las reglas exigen `updatedAt` del servidor, que `editedAt` no
 * retroceda y que los campos de la cita no cambien (ver `firestore.rules`).
 */
class FirestoreNotes @Inject constructor(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth,
) : RemoteNotes {
    override suspend fun push(note: SyncNote): Result<Unit> = remoteCall("pushNote(${note.note.id.take(8)})") {
        val ref = db.document("users/${uid()}/notes/${note.note.id}")
        // Una transacción, no `set`: sin conexión falla enseguida con UNAVAILABLE en lugar de quedar esperando.
        db.runTransaction { tx ->
            val existing = tx.get(ref).takeIf { it.exists() }?.getLong(EDITED_AT)
            // SYN-005: la nube tiene una edición más nueva: no se pisa; llega por el listener.
            if (existing == null || existing <= note.editedAt) tx.set(ref, note.toMap())
            null
        }.await()
        Unit
    }

    override fun observeChanges(sinceMillis: Long?): Flow<Result<RemoteNoteChanges>> = callbackFlow {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            trySend(Result.failure(RemoteUnavailableException("No hay sesión iniciada.")))
            close()
            return@callbackFlow
        }
        val collection = db.collection("users/$uid/notes")
        val query: Query = sinceMillis?.let { collection.whereGreaterThan(UPDATED_AT, Timestamp(Date(it))) } ?: collection
        val registration = query.addSnapshotListener { snapshot, error ->
            if (error != null || snapshot == null) {
                trySend(Result.failure(error?.toRemoteError() ?: RemoteUnavailableException("Sin datos de la nube.")))
                close()
                return@addSnapshotListener
            }
            // Las escrituras de este dispositivo que aún no confirmó el servidor se omiten.
            val docs = snapshot.documentChanges
                .filter { it.type == DocumentChange.Type.ADDED || it.type == DocumentChange.Type.MODIFIED }
                .map { it.document }
                .filter { !it.metadata.hasPendingWrites() }
            if (docs.isNotEmpty()) {
                val newest = docs.mapNotNull { it.getTimestamp(UPDATED_AT)?.toDate()?.time }.maxOrNull()
                trySend(Result.success(RemoteNoteChanges(docs.mapNotNull { it.toSyncNote() }, newest)))
            }
        }
        awaitClose { registration.remove() }
    }

    private fun uid(): String = auth.currentUser?.uid ?: throw RemoteUnavailableException("No hay sesión iniciada.")

    private fun SyncNote.toMap(): Map<String, Any?> = mapOf(
        BOOK_ID to note.bookId,
        LOCATOR_JSON to note.locatorJson,
        QUOTE to note.quote,
        TEXT to note.text,
        PROGRESSION to note.progression.coerceIn(0.0, 1.0),
        CREATED_AT to note.createdAt,
        EDITED_AT to editedAt,
        DELETED_AT to deletedAt,
        UPDATED_AT to FieldValue.serverTimestamp(),
    )

    private fun DocumentSnapshot.toSyncNote(): SyncNote? {
        if (!exists()) return null
        val note = Note(
            id = id,
            bookId = getString(BOOK_ID) ?: return null,
            locatorJson = getString(LOCATOR_JSON) ?: return null,
            quote = getString(QUOTE) ?: return null,
            text = getString(TEXT).orEmpty(),
            progression = getDouble(PROGRESSION) ?: 0.0,
            createdAt = getLong(CREATED_AT) ?: return null,
            updatedAt = getLong(EDITED_AT) ?: return null,
        )
        return SyncNote(note, getLong(DELETED_AT))
    }

    private companion object {
        const val BOOK_ID = "bookId"
        const val LOCATOR_JSON = "locatorJson"
        const val QUOTE = "quote"
        const val TEXT = "text"
        const val PROGRESSION = "progression"
        const val CREATED_AT = "createdAt"
        const val EDITED_AT = "editedAt"
        const val DELETED_AT = "deletedAt"
        const val UPDATED_AT = "updatedAt"
    }
}
