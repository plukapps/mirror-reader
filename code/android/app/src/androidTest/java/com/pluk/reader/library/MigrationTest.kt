package com.pluk.reader.library

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pluk.reader.data.local.db.MIGRATION_1_2
import com.pluk.reader.data.local.db.MIGRATION_2_3
import com.pluk.reader.data.local.db.MIGRATION_3_4
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

    // La cadena completa desde la primera versión sigue funcionando
    @Test
    fun migratesFrom1To4() {
        helper.createDatabase("migration-test-all", 1).close()
        helper.runMigrationsAndValidate("migration-test-all", 4, true, MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
    }
}
