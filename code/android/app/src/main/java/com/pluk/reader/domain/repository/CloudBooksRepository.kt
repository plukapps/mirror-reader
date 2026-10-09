package com.pluk.reader.domain.repository

import com.pluk.reader.domain.remote.RemoteBook
import java.io.File

/** Portada local de un libro. */
data class CoverFile(val bookId: String, val file: File)

/** Libros que viven en la nube y su llegada a este dispositivo (LIB-007). */
interface CloudBooksRepository {
    /** Registra como "solo en la nube" los libros que aún no están en la biblioteca. No toca los que ya existen. */
    suspend fun addCloudOnly(books: List<RemoteBook>)

    /** Libros cuyo archivo aún no está en este dispositivo (LIB-007). */
    suspend fun cloudOnlyBookIds(): List<String>

    /** El archivo del libro ya está en este dispositivo. */
    suspend fun isDownloaded(bookId: String): Boolean

    /** Archivo temporal donde bajar un libro, en el mismo almacenamiento que los libros. */
    fun newTempFile(): File

    /** Libros ya subidos a la nube con portada en este dispositivo cuya portada aún no se sabe en la nube (LIB-012). */
    suspend fun coversToUpload(): List<CoverFile>

    /** Registra que la portada del libro ya está en la nube, para no volver a consultarla (LIB-012). */
    suspend fun markCoverUploaded(bookId: String)

    /** Libros solo en la nube que todavía no tienen portada en este dispositivo (LIB-012). */
    suspend fun cloudBooksWithoutCover(): List<String>

    /** Guarda [downloaded] como portada del libro [bookId] y la marca como disponible. Consume [downloaded]. */
    suspend fun installCover(bookId: String, downloaded: File)

    /** Valida [downloaded], lo guarda como archivo del libro [bookId], extrae la portada y lo marca como descargado. Borra [downloaded]. */
    suspend fun install(bookId: String, downloaded: File): Result<Unit>
}
