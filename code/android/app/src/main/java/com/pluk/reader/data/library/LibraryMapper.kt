package com.pluk.reader.data.library

import com.pluk.reader.data.local.db.BookEntity
import com.pluk.reader.domain.model.LibraryBook
import com.pluk.reader.domain.model.libraryProgress

/** Une el libro guardado con su progresión y la ruta de su portada (LIB-011). */
fun BookEntity.toLibraryBook(progression: Double?, coverPath: String?, lastReadAt: Long? = null): LibraryBook = LibraryBook(
    id = id,
    title = title,
    author = author,
    coverPath = coverPath.takeIf { hasCover },
    progressPercent = libraryProgress(progression),
    lastReadAt = lastReadAt,
    addedAt = addedAt,
    isDownloaded = isDownloaded,
    isUploaded = uploadedAt != null,
)
