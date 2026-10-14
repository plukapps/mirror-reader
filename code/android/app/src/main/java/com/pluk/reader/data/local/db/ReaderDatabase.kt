package com.pluk.reader.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [ReadingPositionEntity::class, BookEntity::class, RemovedBookEntity::class, NoteEntity::class],
    version = 8, exportSchema = true,
)
abstract class ReaderDatabase : RoomDatabase() {
    abstract fun readingPositionDao(): ReadingPositionDao
    abstract fun bookDao(): BookDao
    abstract fun noteDao(): NoteDao
}
