package com.pluk.reader.data.local.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Nota sobre un texto del libro (ANN-003, ANN-006). [id] es un UUID, el mismo en la nube (SYN-016).
 *
 * @param updatedAt última edición o borrado, reloj del dispositivo (`editedAt` en la nube): decide quién gana (SYN-005).
 * @param deletedAt marca de borrado (SYN-007). Las pantallas no ven las notas borradas.
 * @param syncedAt el `updatedAt` que la nube ya tiene, o null si la nota nunca llegó a la nube. Menor que
 * [updatedAt]: hay un cambio pendiente de subir.
 */
@Entity(tableName = "notes", indices = [Index("bookId")])
data class NoteEntity(
    @PrimaryKey val id: String,
    val bookId: String,
    val locatorJson: String,
    val quote: String,
    val text: String,
    val progression: Double,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
    val syncedAt: Long? = null,
)
