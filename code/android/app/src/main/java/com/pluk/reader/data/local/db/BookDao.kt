package com.pluk.reader.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BookDao {
    @Query("SELECT * FROM books ORDER BY addedAt DESC")
    fun observeAll(): Flow<List<BookEntity>>

    @Query("SELECT * FROM books WHERE id = :id")
    suspend fun get(id: String): BookEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: BookEntity)

    /** Libros solo en la nube que aún no estaban en la base. No pisa los que ya existen (LIB-007). */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(entities: List<BookEntity>)

    /** Libros con archivo en este dispositivo que todavía no están en la nube, el más viejo primero. */
    @Query("SELECT * FROM books WHERE isDownloaded = 1 AND uploadedAt IS NULL ORDER BY addedAt ASC")
    suspend fun getPendingUpload(): List<BookEntity>

    @Query("UPDATE books SET uploadedAt = :uploadedAt, sizeBytes = :sizeBytes WHERE id = :id")
    suspend fun markUploaded(id: String, uploadedAt: Long, sizeBytes: Long)

    /** Libros ya subidos que tienen portada en este dispositivo (LIB-012). */
    @Query("SELECT * FROM books WHERE isDownloaded = 1 AND uploadedAt IS NOT NULL AND hasCover = 1")
    suspend fun getUploadedWithCover(): List<BookEntity>

    /** Libros solo en la nube que todavía no tienen portada (LIB-012). */
    @Query("SELECT id FROM books WHERE isDownloaded = 0 AND hasCover = 0")
    suspend fun getCloudOnlyWithoutCover(): List<String>

    @Query("UPDATE books SET hasCover = 1 WHERE id = :id")
    suspend fun markCover(id: String)

    /** El archivo del libro ya está en este dispositivo (descargado o importado de nuevo). */
    @Query("UPDATE books SET isDownloaded = 1, hasCover = :hasCover WHERE id = :id")
    suspend fun markDownloaded(id: String, hasCover: Boolean)
}
