package com.pluk.reader.domain.repository

import com.pluk.reader.domain.remote.RemoteBook
import java.io.File

/**
 * Un libro con archivo en este dispositivo que aún no está en la nube. [RemoteBook.sizeBytes] es el tamaño real
 * del archivo. [cover] es su portada local, o null si no tiene (LIB-012).
 */
data class PendingUpload(val book: RemoteBook, val file: File, val cover: File? = null)

interface BookUploadRepository {
    /** Libros pendientes de subir, el más viejo primero. Se omiten los que perdieron su archivo local. */
    suspend fun pending(): List<PendingUpload>

    /** Registra que el libro ya está en la nube. [coverUploaded]: su portada también (LIB-012). */
    suspend fun markUploaded(bookId: String, sizeBytes: Long, coverUploaded: Boolean)
}
