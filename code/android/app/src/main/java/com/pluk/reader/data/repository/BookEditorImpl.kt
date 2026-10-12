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
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class BookEditorImpl @Inject constructor(
    @ApplicationContext context: Context,
    private val db: ReaderDatabase,
    private val files: LibraryFiles,
) : BookEditor {
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
        db.withTransaction {
            db.bookDao().insertRemoved(RemovedBookEntity(bookId, System.currentTimeMillis()))
            db.bookDao().delete(bookId)
            db.readingPositionDao().delete(bookId)
        }
        files.bookFile(bookId).delete()
        files.coverFile(bookId).delete()
        Unit
    }
}
