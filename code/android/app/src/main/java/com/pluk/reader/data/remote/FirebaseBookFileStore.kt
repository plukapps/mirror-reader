package com.pluk.reader.data.remote

import android.net.Uri
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageException
import com.google.firebase.storage.StorageReference
import com.google.firebase.storage.storageMetadata
import com.pluk.reader.domain.remote.BookFileStore
import com.pluk.reader.domain.remote.RemoteUnavailableException
import kotlinx.coroutines.tasks.await
import java.io.File
import javax.inject.Inject

/** Archivos en `users/{uid}/books/{sha256}.epub` (ver `specs/platforms/backend.md`). */
class FirebaseBookFileStore @Inject constructor(
    private val storage: FirebaseStorage,
    private val auth: FirebaseAuth,
) : BookFileStore {
    override suspend fun upload(bookId: String, file: File): Result<Unit> = remoteCall {
        val ref = bookRef(bookId)
        // Un libro ya subido no se vuelve a subir: el nombre es su hash y las reglas no permiten sobrescribir (LIB-002).
        if (!exists(ref)) {
            ref.putFile(Uri.fromFile(file), storageMetadata { contentType = EPUB_MIME_TYPE }).await()
        }
    }

    override suspend fun download(bookId: String, destination: File): Result<Unit> = remoteCall {
        bookRef(bookId).getFile(destination).await()
        Unit
    }

    private suspend fun exists(ref: StorageReference): Boolean = try {
        ref.metadata.await()
        true
    } catch (e: StorageException) {
        if (e.errorCode == StorageException.ERROR_OBJECT_NOT_FOUND) false else throw e
    }

    private fun bookRef(bookId: String): StorageReference {
        val uid = auth.currentUser?.uid ?: throw RemoteUnavailableException("No hay sesión iniciada.")
        return storage.reference.child("users/$uid/books/$bookId.epub")
    }

    private companion object {
        const val EPUB_MIME_TYPE = "application/epub+zip"
    }
}
