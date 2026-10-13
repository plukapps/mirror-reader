package com.pluk.reader.data

import android.content.Context
import android.content.ContextWrapper
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pluk.reader.data.account.LocalDataWiper
import com.pluk.reader.data.library.LibraryFiles
import com.pluk.reader.data.local.db.BookEntity
import com.pluk.reader.data.local.db.ReaderDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

/** PRF-009: cerrar sesión borra lo de la cuenta y deja los ajustes del lector y la identidad del dispositivo. */
@RunWith(AndroidJUnit4::class)
class LocalDataWiperTest {
    private val target: Context = InstrumentationRegistry.getInstrumentation().targetContext

    // Una carpeta propia: el test corre con los datos de la app y no debe tocar los libros reales.
    private val sandbox = File(target.cacheDir, "wiper-${UUID.randomUUID()}").apply { mkdirs() }
    private val context = object : ContextWrapper(target) {
        override fun getFilesDir(): File = sandbox
        override fun getApplicationContext(): Context = this
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val dataStore = PreferenceDataStoreFactory.create(scope = scope) { File(sandbox, "prefs.preferences_pb") }
    private val db = Room.inMemoryDatabaseBuilder(target, ReaderDatabase::class.java).build()
    private val files = LibraryFiles(context)

    @After
    fun tearDown() {
        db.close()
        scope.cancel()
        sandbox.deleteRecursively()
    }

    @Test
    fun wipesAccountDataAndKeepsReaderSettings() = runBlocking {
        db.bookDao().insert(BookEntity("b1", "Drácula", null, hasCover = true, addedAt = 1))
        files.bookFile("b1").writeText("epub")
        files.coverFile("b1").writeText("jpg")
        dataStore.edit {
            it[stringPreferencesKey("theme")] = "SEPIA"
            it[stringPreferencesKey("sync_device_id")] = "d1"
            it[longPreferencesKey("sync_last_positions_seen_at")] = 5
            it[longPreferencesKey("profile_used_bytes")] = 10
        }

        LocalDataWiper(db, files, dataStore).wipe()

        assertTrue(db.bookDao().getAllIds().isEmpty())
        assertFalse(files.bookFile("b1").exists())
        assertFalse(files.coverFile("b1").exists())
        val prefs = dataStore.data.first()
        assertEquals("SEPIA", prefs[stringPreferencesKey("theme")])
        assertEquals("d1", prefs[stringPreferencesKey("sync_device_id")])
        assertNull(prefs[longPreferencesKey("sync_last_positions_seen_at")])
        assertNull(prefs[longPreferencesKey("profile_used_bytes")])
    }
}
