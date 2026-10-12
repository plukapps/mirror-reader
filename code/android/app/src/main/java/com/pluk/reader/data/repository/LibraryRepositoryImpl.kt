package com.pluk.reader.data.repository

import android.net.Uri
import com.pluk.reader.data.library.EpubImporter
import com.pluk.reader.data.library.LibraryFiles
import com.pluk.reader.data.library.toLibraryBook
import com.pluk.reader.data.local.db.BookDao
import com.pluk.reader.data.local.db.ReadingPositionDao
import com.pluk.reader.domain.model.ImportOutcome
import com.pluk.reader.domain.model.LibraryBook
import com.pluk.reader.domain.model.positionProgression
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
            val positionById = positions.associateBy { it.bookId }
            books.map { book ->
                val position = positionById[book.id]
                val progression = position?.let { positionProgression(it.locatorJson, it.progress) }
                book.toLibraryBook(progression, files.coverFile(book.id).absolutePath, position?.updatedAt)
            }
        }

    override suspend fun import(uri: String): ImportOutcome = importer.import(Uri.parse(uri))
}
