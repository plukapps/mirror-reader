package com.pluk.reader.data.local.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ReadingPositionDao {
    @Query("SELECT * FROM reading_positions WHERE bookId = :bookId")
    suspend fun get(bookId: String): ReadingPositionEntity?

    @Query("SELECT bookId, progress FROM reading_positions")
    fun observeProgress(): Flow<List<BookProgress>>

    @Upsert
    suspend fun upsert(entity: ReadingPositionEntity)
}

/** Progresión guardada de un libro. */
data class BookProgress(val bookId: String, val progress: Double?)
