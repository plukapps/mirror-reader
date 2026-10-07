package com.pluk.reader.data.repository

import android.net.Uri
import com.pluk.reader.data.epub.PublicationLoader
import com.pluk.reader.data.library.LibraryFiles
import com.pluk.reader.domain.model.BookOpenException
import com.pluk.reader.domain.model.OpenedBook
import com.pluk.reader.domain.repository.BookRepository
import com.pluk.reader.domain.repository.PositionRepository
import javax.inject.Inject

class BookRepositoryImpl @Inject constructor(
    private val loader: PublicationLoader,
    private val positions: PositionRepository,
    private val files: LibraryFiles,
) : BookRepository {
    override suspend fun open(bookId: String): Result<OpenedBook> {
        val file = files.bookFile(bookId)
        if (!file.exists()) return Result.failure(BookOpenException("El libro ya no está en el dispositivo."))
        return loader.load(Uri.fromFile(file)).map { OpenedBook(it, bookId, positions.get(bookId)) }
    }
}
