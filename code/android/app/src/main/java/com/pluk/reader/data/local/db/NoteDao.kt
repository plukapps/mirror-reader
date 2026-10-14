package com.pluk.reader.data.local.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {
    /** Notas del libro por su lugar en el libro; a igual lugar, la más vieja primero (ANN-010). */
    @Query("SELECT * FROM notes WHERE bookId = :bookId ORDER BY progression, createdAt")
    fun observeByBook(bookId: String): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun get(id: String): NoteEntity?

    @Upsert
    suspend fun upsert(entity: NoteEntity)

    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun delete(id: String)

    /** Al quitar el libro se van sus notas (ANN-011). */
    @Query("DELETE FROM notes WHERE bookId = :bookId")
    suspend fun deleteByBook(bookId: String)
}
