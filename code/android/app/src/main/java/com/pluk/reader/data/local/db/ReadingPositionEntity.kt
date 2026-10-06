package com.pluk.reader.data.local.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "reading_positions")
data class ReadingPositionEntity(
    @PrimaryKey val bookId: String,
    val locatorJson: String,
    val updatedAt: Long,
)
