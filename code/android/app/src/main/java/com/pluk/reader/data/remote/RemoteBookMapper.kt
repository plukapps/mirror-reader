package com.pluk.reader.data.remote

import com.pluk.reader.data.local.db.BookEntity
import com.pluk.reader.domain.remote.RemoteBook

/** Metadatos para subir. El autor local es un texto; en la nube es una lista (ver `backend.md`). */
fun BookEntity.toRemoteBook(sizeBytes: Long): RemoteBook = RemoteBook(
    id = id,
    title = title,
    authors = listOfNotNull(author?.takeIf { it.isNotBlank() }),
    sizeBytes = sizeBytes,
)

/** Un libro que existe en la nube pero no en este dispositivo (LIB-007). Sin archivo ni portada hasta descargarlo. */
fun RemoteBook.toCloudOnlyEntity(now: Long): BookEntity = BookEntity(
    id = id,
    title = title,
    author = authors.filter { it.isNotBlank() }.joinToString(", ").ifBlank { null },
    hasCover = false,
    addedAt = now,
    sizeBytes = sizeBytes,
    isDownloaded = false,
    uploadedAt = now,
)
