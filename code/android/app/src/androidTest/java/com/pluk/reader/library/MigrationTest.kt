package com.pluk.reader.library

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pluk.reader.data.local.db.MIGRATION_1_2
import com.pluk.reader.data.local.db.MIGRATION_2_3
import com.pluk.reader.data.local.db.MIGRATION_3_4
import com.pluk.reader.data.local.db.MIGRATION_4_5
import com.pluk.reader.data.local.db.MIGRATION_5_6
import com.pluk.reader.data.local.db.MIGRATION_6_7
import com.pluk.reader.data.local.db.MIGRATION_7_8
import com.pluk.reader.data.local.db.MIGRATION_8_9
import com.pluk.reader.data.local.db.ReaderDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        ReaderDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    // LIB-003: migrar no pierde las posiciones de lectura existentes y crea la tabla de libros
    @Test
    fun migrates1To2KeepingPositions() {
        helper.createDatabase("migration-test", 1).apply {
            execSQL("INSERT INTO reading_positions (bookId, locatorJson, updatedAt) VALUES ('b', '{}', 1)")
            close()
        }
        val db = helper.runMigrationsAndValidate("migration-test", 2, true, MIGRATION_1_2)
        db.query("SELECT COUNT(*) FROM reading_positions").use {
            it.moveToFirst()
            assertEquals(1, it.getInt(0))
        }
        db.query("SELECT COUNT(*) FROM books").use {
            it.moveToFirst()
            assertEquals(0, it.getInt(0))
        }
    }

    // LIB-007: migrar a la versión 3 conserva los libros y los deja como descargados y aún no subidos
    @Test
    fun migrates2To3KeepingBooksAsDownloadedAndNotUploaded() {
        helper.createDatabase("migration-test-3", 2).apply {
            execSQL("INSERT INTO books (id, title, author, hasCover, addedAt) VALUES ('h', 'Moby Dick', 'Melville', 1, 5)")
            close()
        }
        val db = helper.runMigrationsAndValidate("migration-test-3", 3, true, MIGRATION_2_3)
        db.query("SELECT title, author, hasCover, addedAt, sizeBytes, isDownloaded, uploadedAt FROM books WHERE id = 'h'").use {
            assertTrue(it.moveToFirst())
            assertEquals("Moby Dick", it.getString(0))
            assertEquals("Melville", it.getString(1))
            assertEquals(1, it.getInt(2))
            assertEquals(5L, it.getLong(3))
            assertEquals(0L, it.getLong(4))
            assertEquals(1, it.getInt(5))
            assertTrue(it.isNull(6))
        }
    }

    // LIB-012: migrar a la versión 4 conserva los libros y deja todas las portadas como no anotadas en la nube
    @Test
    fun migrates3To4KeepingBooksWithCoversNotMarkedUploaded() {
        helper.createDatabase("migration-test-4", 3).apply {
            execSQL(
                "INSERT INTO books (id, title, author, hasCover, addedAt, sizeBytes, isDownloaded, uploadedAt) " +
                    "VALUES ('h', 'Moby Dick', 'Melville', 1, 5, 2048, 1, 9)",
            )
            close()
        }
        val db = helper.runMigrationsAndValidate("migration-test-4", 4, true, MIGRATION_3_4)
        db.query("SELECT title, hasCover, sizeBytes, uploadedAt, isCoverUploaded FROM books WHERE id = 'h'").use {
            assertTrue(it.moveToFirst())
            assertEquals("Moby Dick", it.getString(0))
            assertEquals(1, it.getInt(1))
            assertEquals(2048L, it.getLong(2))
            assertEquals(9L, it.getLong(3))
            assertEquals(0, it.getInt(4))
        }
    }

    // SYN-011: migrar a la versión 5 conserva las posiciones y las deja pendientes de enviar
    @Test
    fun migrates4To5KeepingPositionsAsNotSynced() {
        helper.createDatabase("migration-test-5", 4).apply {
            execSQL("INSERT INTO reading_positions (bookId, locatorJson, updatedAt, progress) VALUES ('b', '{\"href\":\"c1\"}', 7, 0.25)")
            close()
        }
        val db = helper.runMigrationsAndValidate("migration-test-5", 5, true, MIGRATION_4_5)
        db.query("SELECT locatorJson, updatedAt, progress, isSynced FROM reading_positions WHERE bookId = 'b'").use {
            assertTrue(it.moveToFirst())
            assertEquals("{\"href\":\"c1\"}", it.getString(0))
            assertEquals(7L, it.getLong(1))
            assertEquals(0.25, it.getDouble(2), 0.0)
            assertEquals(0, it.getInt(3))
        }
    }

    // LIB-019, LIB-020: migrar a la versión 6 conserva los libros, con portada sin reemplazar y sin libros quitados
    @Test
    fun migrates5To6KeepingBooks() {
        helper.createDatabase("migration-test-6", 5).apply {
            execSQL(
                "INSERT INTO books (id, title, author, hasCover, addedAt, sizeBytes, isDownloaded, uploadedAt, isCoverUploaded) " +
                    "VALUES ('h', 'Drácula', 'Stoker', 1, 5, 10, 1, 9, 1)",
            )
            close()
        }
        val db = helper.runMigrationsAndValidate("migration-test-6", 6, true, MIGRATION_5_6)
        db.query("SELECT title, hasCover, isCoverUploaded, coverVersion FROM books WHERE id = 'h'").use {
            assertTrue(it.moveToFirst())
            assertEquals("Drácula", it.getString(0))
            assertEquals(1, it.getInt(1))
            assertEquals(1, it.getInt(2))
            assertEquals(0L, it.getLong(3))
        }
        db.query("SELECT COUNT(*) FROM removed_books").use {
            it.moveToFirst()
            assertEquals(0, it.getInt(0))
        }
    }

    // LIB-020: los libros quitados antes de la versión 7 se quitaron solo de este dispositivo: no se borran de la nube
    @Test
    fun migrates6To7KeepingOldRemovalsLocal() {
        helper.createDatabase("migration-test-7", 6).apply {
            execSQL("INSERT INTO removed_books (id, removedAt) VALUES ('q', 3)")
            close()
        }
        val db = helper.runMigrationsAndValidate("migration-test-7", 7, true, MIGRATION_6_7)
        db.query("SELECT removedAt, cloudDeleted FROM removed_books WHERE id = 'q'").use {
            assertTrue(it.moveToFirst())
            assertEquals(3L, it.getLong(0))
            assertEquals(1, it.getInt(1))
        }
    }

    // ANN-003: la versión 8 agrega la tabla de notas, vacía, sin tocar los libros
    @Test
    fun migrates7To8AddingEmptyNotes() {
        helper.createDatabase("migration-test-8", 7).apply {
            execSQL("INSERT INTO reading_positions (bookId, locatorJson, updatedAt) VALUES ('b', '{}', 1)")
            close()
        }
        val db = helper.runMigrationsAndValidate("migration-test-8", 8, true, MIGRATION_7_8)
        db.query("SELECT COUNT(*) FROM notes").use {
            it.moveToFirst()
            assertEquals(0, it.getInt(0))
        }
        db.query("SELECT COUNT(*) FROM reading_positions").use {
            it.moveToFirst()
            assertEquals(1, it.getInt(0))
        }
    }

    // SYN-016: las notas de antes de la versión 9 quedan vivas y pendientes de subir
    @Test
    fun migrates8To9KeepingNotesPending() {
        helper.createDatabase("migration-test-9", 8).apply {
            execSQL(
                "INSERT INTO notes (id, bookId, locatorJson, quote, text, progression, createdAt, updatedAt) " +
                    "VALUES ('n', 'b', '{}', 'cita', 'nota', 0.5, 1, 2)",
            )
            close()
        }
        val db = helper.runMigrationsAndValidate("migration-test-9", 9, true, MIGRATION_8_9)
        db.query("SELECT text, deletedAt, syncedAt FROM notes WHERE id = 'n'").use {
            assertTrue(it.moveToFirst())
            assertEquals("nota", it.getString(0))
            assertTrue(it.isNull(1))
            assertTrue(it.isNull(2))
        }
    }

    // La cadena completa desde la primera versión sigue funcionando
    @Test
    fun migratesFrom1To9() {
        helper.createDatabase("migration-test-all", 1).close()
        helper.runMigrationsAndValidate(
            "migration-test-all", 9, true,
            MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8,
            MIGRATION_8_9,
        )
    }
}
