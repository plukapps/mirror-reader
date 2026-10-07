package com.pluk.reader.data.repository

import android.net.Uri
import com.pluk.reader.data.library.EpubImporter
import com.pluk.reader.data.library.LibraryFiles
import com.pluk.reader.data.library.toLibraryBook
import com.pluk.reader.data.local.db.BookDao
import com.pluk.reader.data.local.db.ReadingPositionDao
import com.pluk.reader.domain.model.ImportOutcome
import com.pluk.reader.domain.model.LibraryBook
import com.pluk.reader.domain.repository.LibraryRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

class LibraryRepositoryImpl @Inject constructor(
    private val bookDao: BookDao,
    private val positionDao: ReadingPositionDao,
    private val files: LibraryFiles,
    private val importer: EpubImporter,
) : LibraryRepository {
    override val books: Flow<List<LibraryBook>> =
        combine(bookDao.observeAll(), positionDao.observeProgress()) { books, positions ->
            val progressById = positions.associate { it.bookId to it.progress }
            books.map { book ->
                // Una posición sin progresión (libro leído antes de LIB-011) cuenta como 0 %: ya se abrió.
                val progression = if (book.id in progressById) progressById[book.id] ?: 0.0 else null
                book.toLibraryBook(progression, files.coverFile(book.id).absolutePath)
            }
        }

    override suspend fun import(uri: String): ImportOutcome = importer.import(Uri.parse(uri))
}
