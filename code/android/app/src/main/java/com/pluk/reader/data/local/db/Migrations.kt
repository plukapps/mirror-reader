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

/**
 * Prepara la sincronización de la posición de lectura (SYN-011). Las posiciones existentes quedan como no
 * enviadas: la primera sincronización las sube una vez.
 */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `reading_positions` ADD COLUMN `isSynced` INTEGER NOT NULL DEFAULT 0")
    }
}

/**
 * Acciones del libro (LIB-019, LIB-020): tabla de libros quitados de este dispositivo y versión de la portada que
 * eligió el usuario. Aditiva: los datos existentes no cambian.
 */
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `removed_books` (`id` TEXT NOT NULL, `removedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))",
        )
        db.execSQL("ALTER TABLE `books` ADD COLUMN `coverVersion` INTEGER NOT NULL DEFAULT 0")
    }
}

/**
 * Quitar borra de la cuenta (LIB-020): cada libro quitado recuerda si la nube ya lo tiene borrado. Los quitados antes
 * de esta versión se quitaron solo de este dispositivo: quedan como hechos y no se borran de la nube.
 */
val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `removed_books` ADD COLUMN `cloudDeleted` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("UPDATE `removed_books` SET `cloudDeleted` = 1")
    }
}

/** Notas sobre un texto del libro (ANN-003). */
val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `notes` (`id` TEXT NOT NULL, `bookId` TEXT NOT NULL, `locatorJson` TEXT NOT NULL, " +
                "`quote` TEXT NOT NULL, `text` TEXT NOT NULL, `progression` REAL NOT NULL, `createdAt` INTEGER NOT NULL, " +
                "`updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_notes_bookId` ON `notes` (`bookId`)")
    }
}
