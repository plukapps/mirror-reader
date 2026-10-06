package com.pluk.reader.data.repository

import android.net.Uri
import com.pluk.reader.data.epub.PublicationLoader
import com.pluk.reader.domain.model.OpenedBook
import com.pluk.reader.domain.repository.BookRepository
import com.pluk.reader.domain.repository.PositionRepository
import javax.inject.Inject

class BookRepositoryImpl @Inject constructor(
    private val loader: PublicationLoader,
    private val positions: PositionRepository,
) : BookRepository {
    override suspend fun open(uri: String): Result<OpenedBook> =
        loader.load(Uri.parse(uri)).map { publication ->
            // La identidad por hash de contenido (LIB-003) llega con la biblioteca.
            val bookId = publication.metadata.identifier ?: uri
            OpenedBook(publication, bookId, positions.get(bookId))
        }
}
