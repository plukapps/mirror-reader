package com.pluk.reader.data.local.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Nota sobre un texto del libro (ANN-003, ANN-006). [id] es un UUID, estable para sincronizar más adelante. */
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
)
