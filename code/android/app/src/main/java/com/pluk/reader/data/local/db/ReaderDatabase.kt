package com.pluk.reader.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [ReadingPositionEntity::class], version = 1, exportSchema = true)
abstract class ReaderDatabase : RoomDatabase() {
    abstract fun readingPositionDao(): ReadingPositionDao
}
