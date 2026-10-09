package com.pluk.reader.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ReadingPositionDao {
    @Query("SELECT * FROM reading_positions WHERE bookId = :bookId")
    suspend fun get(bookId: String): ReadingPositionEntity?

    @Query("SELECT bookId, progress, updatedAt FROM reading_positions")
    fun observeProgress(): Flow<List<BookProgress>>

    @Upsert
    suspend fun upsert(entity: ReadingPositionEntity)

    /**
     * Posiciones que la nube aún no tiene y cuyo libro ya está en la nube, para enviarlas (SYN-011). La regla del
     * servidor exige que el libro exista; enviar antes solo produce rechazos que se repiten en cada intento. Los
     * libros de antes del hash (ids que no son SHA-256) nunca se suben, así que su posición queda solo aquí.
     */
    @Query(
        "SELECT p.* FROM reading_positions p INNER JOIN books b ON b.id = p.bookId " +
            "WHERE p.isSynced = 0 AND b.uploadedAt IS NOT NULL",
    )
    suspend fun getPending(): List<ReadingPositionEntity>

    /** Vuelve a marcar la posición como pendiente de enviar. */
    @Query("UPDATE reading_positions SET isSynced = 0 WHERE bookId = :bookId")
    suspend fun markPending(bookId: String)

    /** Primer paso de guardar una posición de la nube: crea la fila si no existía. No pisa una existente. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfMissing(entity: ReadingPositionEntity)

    /** Segundo paso: reemplaza la posición solo si la de la nube es una lectura más reciente (SYN-003). */
    @Query(
        "UPDATE reading_positions SET locatorJson = :locatorJson, progress = :progress, updatedAt = :readAt, isSynced = 1 " +
            "WHERE bookId = :bookId AND updatedAt < :readAt",
    )
    suspend fun replaceIfOlder(bookId: String, locatorJson: String, progress: Double?, readAt: Long)

    /**
     * Marca como enviada la lectura de [readAt]. Si mientras tanto se leyó más, la fila ya tiene otro
     * `updatedAt` y sigue pendiente.
     */
    @Query("UPDATE reading_positions SET isSynced = 1 WHERE bookId = :bookId AND updatedAt = :readAt")
    suspend fun markSynced(bookId: String, readAt: Long)
}

/** Progresión guardada de un libro. */
data class BookProgress(val bookId: String, val progress: Double?, val updatedAt: Long)
