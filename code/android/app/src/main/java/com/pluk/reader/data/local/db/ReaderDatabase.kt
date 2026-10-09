package com.pluk.reader.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [ReadingPositionEntity::class, BookEntity::class], version = 4, exportSchema = true)
abstract class ReaderDatabase : RoomDatabase() {
    abstract fun readingPositionDao(): ReadingPositionDao
    abstract fun bookDao(): BookDao
}
