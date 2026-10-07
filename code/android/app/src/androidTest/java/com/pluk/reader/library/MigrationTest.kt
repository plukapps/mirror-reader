package com.pluk.reader.library

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pluk.reader.data.local.db.MIGRATION_1_2
import com.pluk.reader.data.local.db.ReaderDatabase
import org.junit.Assert.assertEquals
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
}
