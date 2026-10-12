package com.pluk.reader.data.remote

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageException
import com.google.firebase.storage.StorageReference
import com.pluk.reader.domain.remote.RemoteRemovals
import com.pluk.reader.domain.remote.RemoteUnavailableException
import javax.inject.Inject
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Borrar un libro de la cuenta (LIB-020): `deletedAt` en `users/{uid}/books/{id}` (las reglas no permiten borrar el
 * documento, SYN-007) y los archivos de Storage. La función `onBookFileDeleted` descuenta la cuota.
 */
class FirebaseRemovals @Inject constructor(
    private val db: FirebaseFirestore,
    private val storage: FirebaseStorage,
    private val auth: FirebaseAuth,
) : RemoteRemovals {
    override suspend fun markDeleted(bookId: String): Result<Unit> = remoteCall("markDeleted(${bookId.take(8)})") {
        try {
            db.document("users/${uid()}/books/$bookId")
                .update(mapOf(DELETED_AT to FieldValue.serverTimestamp(), UPDATED_AT to FieldValue.serverTimestamp()))
                .await()
        } catch (e: FirebaseFirestoreException) {
            // Sin documento no hay nada que marcar.
            if (e.code != FirebaseFirestoreException.Code.NOT_FOUND) throw e
        }
        Unit
    }

    override suspend fun deleteFiles(bookId: String): Result<Unit> = remoteCall("deleteFiles(${bookId.take(8)})") {
        val uid = uid()
        deleteIfExists(storage.reference.child("users/$uid/books/$bookId.epub"))
        deleteIfExists(storage.reference.child("users/$uid/covers/$bookId.jpg"))
    }

    override suspend fun deletedBookIds(): Result<List<String>> = remoteCall("deletedBookIds") {
        db.collection("users/${uid()}/books").whereNotEqualTo(DELETED_AT, null).get().await().documents.map { it.id }
    }

    override fun observeDeleted(): Flow<Result<List<String>>> = callbackFlow {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            trySend(Result.failure(RemoteUnavailableException("No hay sesión iniciada.")))
            close()
            return@callbackFlow
        }
        val registration = db.collection("users/$uid/books").whereNotEqualTo(DELETED_AT, null)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) {
                    trySend(Result.failure(error?.toRemoteError() ?: RemoteUnavailableException("Sin datos de la nube.")))
                    close()
                    return@addSnapshotListener
                }
                trySend(Result.success(snapshot.documents.map { it.id }))
            }
        awaitClose { registration.remove() }
    }

    private suspend fun deleteIfExists(ref: StorageReference) {
        try {
            ref.delete().await()
        } catch (e: StorageException) {
            if (e.errorCode != StorageException.ERROR_OBJECT_NOT_FOUND) throw e
        }
    }

    private fun uid(): String = auth.currentUser?.uid ?: throw RemoteUnavailableException("No hay sesión iniciada.")

    private companion object {
        const val DELETED_AT = "deletedAt"
        const val UPDATED_AT = "updatedAt"
    }
}
