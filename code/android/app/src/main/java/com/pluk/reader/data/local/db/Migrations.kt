package com.pluk.reader.data.local.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Agrega la tabla de libros de la biblioteca y la progresión de cada posición guardada. */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `books` (" +
                "`id` TEXT NOT NULL, `title` TEXT NOT NULL, `author` TEXT, " +
                "`hasCover` INTEGER NOT NULL, `addedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))",
        )
        db.execSQL("ALTER TABLE `reading_positions` ADD COLUMN `progress` REAL")
    }
}
