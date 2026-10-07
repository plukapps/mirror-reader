package com.pluk.reader.data.local.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "reading_positions")
data class ReadingPositionEntity(
    @PrimaryKey val bookId: String,
    val locatorJson: String,
    val updatedAt: Long,
    /** Progresión total 0.0..1.0 al guardar, para la biblioteca (LIB-011). Null si se desconoce. */
    val progress: Double? = null,
)
