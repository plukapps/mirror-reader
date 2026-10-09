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

/**
 * Prepara la biblioteca para la nube (LIB-007). Los libros existentes quedan como descargados y no
 * subidos, que es lo que son: ninguno estuvo nunca en la nube.
 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `books` ADD COLUMN `sizeBytes` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE `books` ADD COLUMN `isDownloaded` INTEGER NOT NULL DEFAULT 1")
        db.execSQL("ALTER TABLE `books` ADD COLUMN `uploadedAt` INTEGER")
    }
}

/**
 * Registra qué portadas ya están en la nube (LIB-012). Todas quedan como no subidas: la próxima sincronización
 * las comprueba una vez y las marca, así no hay que asumir nada sobre lo que había en la nube.
 */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `books` ADD COLUMN `isCoverUploaded` INTEGER NOT NULL DEFAULT 0")
    }
}
