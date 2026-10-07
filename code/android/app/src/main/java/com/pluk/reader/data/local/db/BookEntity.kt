package com.pluk.reader.data.local.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Libro de la biblioteca. [id] es el SHA-256 del contenido (LIB-003, ADR 0006). */
@Entity(tableName = "books")
data class BookEntity(
    @PrimaryKey val id: String,
    val title: String,
    val author: String?,
    val hasCover: Boolean,
    val addedAt: Long,
)
