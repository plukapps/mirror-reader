package com.pluk.reader.data.remote

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.pluk.reader.domain.remote.RemoteBook
import com.pluk.reader.domain.remote.RemoteLibrary
import com.pluk.reader.domain.remote.RemoteUnavailableException
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

/**
 * Metadatos en `users/{uid}/books/{bookId}`. Las reglas exigen fechas del servidor y que `filePath`,
 * `sizeBytes` y `createdAt` no cambien una vez creado el documento (ver `firestore.rules`).
 */
class FirestoreRemoteLibrary @Inject constructor(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth,
) : RemoteLibrary {
    override suspend fun listBooks(): Result<List<RemoteBook>> = remoteCall {
        val uid = uid()
        db.collection("users/$uid/books").whereEqualTo(DELETED_AT, null).get().await().documents.mapNotNull { doc ->
            val title = doc.getString(TITLE) ?: return@mapNotNull null
            @Suppress("UNCHECKED_CAST")
            val authors = (doc.get(AUTHORS) as? List<String>).orEmpty()
            RemoteBook(doc.id, title, authors, doc.getLong(SIZE_BYTES) ?: 0L)
        }
    }.onSuccess { Log.i(TAG, "Libros en la nube: ${it.size}") }
        .onFailure { Log.w(TAG, "No se pudo listar la nube: $it") }

    override suspend fun saveBook(book: RemoteBook): Result<Unit> = remoteCall {
        val uid = uid()
        val ref: DocumentReference = db.document("users/$uid/books/${book.id}")
        db.runTransaction { tx ->
            if (tx.get(ref).exists()) {
                // Solo lo editable: la regla rechaza cambios en filePath, sizeBytes y createdAt.
                tx.update(ref, mapOf(TITLE to book.title, AUTHORS to book.authors, UPDATED_AT to FieldValue.serverTimestamp()))
            } else {
                tx.set(
                    ref,
                    mapOf(
                        TITLE to book.title,
                        AUTHORS to book.authors,
                        FILE_PATH to "users/$uid/books/${book.id}.epub",
                        SIZE_BYTES to book.sizeBytes,
                        CREATED_AT to FieldValue.serverTimestamp(),
                        UPDATED_AT to FieldValue.serverTimestamp(),
                        DELETED_AT to null,
                    ),
                )
            }
            null
        }.await()
        Unit
    }

    private fun uid(): String = auth.currentUser?.uid ?: throw RemoteUnavailableException("No hay sesión iniciada.")

    private companion object {
        const val TAG = "RemoteLibrary"
        const val TITLE = "title"
        const val AUTHORS = "authors"
        const val FILE_PATH = "filePath"
        const val SIZE_BYTES = "sizeBytes"
        const val CREATED_AT = "createdAt"
        const val UPDATED_AT = "updatedAt"
        const val DELETED_AT = "deletedAt"
    }
}
