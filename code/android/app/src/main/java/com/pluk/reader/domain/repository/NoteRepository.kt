package com.pluk.reader.domain.repository

import com.pluk.reader.domain.model.Note
import kotlinx.coroutines.flow.Flow

/** Notas de los libros, solo en este dispositivo por ahora (ANN-003, ANN-007 queda para la sincronización). */
interface NoteRepository {
    /** Notas del libro ordenadas por su lugar en el libro (ANN-010). */
    fun observe(bookId: String): Flow<List<Note>>

    suspend fun get(id: String): Note?

    /** Crea la nota o reemplaza la que tiene el mismo id (ANN-005). */
    suspend fun save(note: Note)

    suspend fun delete(id: String)
}
