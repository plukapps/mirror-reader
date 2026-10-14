package com.pluk.reader.data.repository

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.pluk.reader.data.library.LibraryFiles
import com.pluk.reader.data.library.decodeCoverImage
import com.pluk.reader.data.library.saveCover
import com.pluk.reader.data.local.db.ReaderDatabase
import com.pluk.reader.data.local.db.RemovedBookEntity
import com.pluk.reader.domain.repository.BookEditor
import com.pluk.reader.domain.repository.BookRemovals
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class BookEditorImpl @Inject constructor(
    @ApplicationContext context: Context,
    private val db: ReaderDatabase,
    private val files: LibraryFiles,
) : BookEditor, BookRemovals {
    private val resolver = context.applicationContext.contentResolver

    override suspend fun replaceCover(bookId: String, imageUri: String): Boolean = withContext(Dispatchers.IO) {
        val bitmap = decodeCoverImage(resolver, Uri.parse(imageUri)) ?: return@withContext false
        val target = files.coverFile(bookId)
        // Se escribe aparte y se reemplaza de una vez: una pantalla nunca lee una portada a medio escribir.
        val temp = File(target.parentFile, "$bookId.jpg.tmp")
        if (!saveCover(bitmap, temp) || !temp.renameTo(target)) {
            temp.delete()
            return@withContext false
        }
        db.bookDao().setCustomCover(bookId, System.currentTimeMillis())
        true
    }

    override suspend fun remove(bookId: String) = withContext(Dispatchers.IO) {
        // Un libro que nunca se subió no tiene nada en la nube: queda hecho al quitarlo.
        val inCloud = db.bookDao().get(bookId)?.uploadedAt != null
        removeLocally(bookId, cloudDeleted = !inCloud)
    }

    override suspend fun pendingCloudRemovals(): List<String> = db.bookDao().getPendingCloudRemovals()

    override suspend fun markCloudRemoved(bookId: String) = db.bookDao().markCloudRemoved(bookId)

    override suspend fun removeDeletedInCloud(bookIds: List<String>) = withContext(Dispatchers.IO) {
        for (bookId in bookIds) {
            val book = db.bookDao().get(bookId) ?: continue
            if (book.uploadedAt != null) removeLocally(bookId, cloudDeleted = true)
        }
    }

    private suspend fun removeLocally(bookId: String, cloudDeleted: Boolean) {
        db.withTransaction {
            db.bookDao().insertRemoved(RemovedBookEntity(bookId, System.currentTimeMillis(), cloudDeleted))
            db.bookDao().delete(bookId)
            db.readingPositionDao().delete(bookId)
            // ANN-011, SYN-007: las notas que la nube tiene se marcan borradas, así el borrado llega a los otros dispositivos.
            db.noteDao().deleteNeverSyncedOfBook(bookId)
            db.noteDao().markDeletedOfBook(bookId, System.currentTimeMillis())
        }
        files.bookFile(bookId).delete()
        files.coverFile(bookId).delete()
    }
}
