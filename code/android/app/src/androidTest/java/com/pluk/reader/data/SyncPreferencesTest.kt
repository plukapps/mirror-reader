package com.pluk.reader.data

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pluk.reader.data.repository.SyncPreferencesImpl
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** SYN-011, SYN-012: la identidad del dispositivo y hasta dónde se sincronizaron las posiciones. */
@RunWith(AndroidJUnit4::class)
class SyncPreferencesTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val file = File(context.cacheDir, "sync-${UUID.randomUUID()}.preferences_pb")
    private val prefs = SyncPreferencesImpl(PreferenceDataStoreFactory.create(scope = scope) { file })

    @After
    fun tearDown() {
        scope.cancel()
        file.delete()
    }

    @Test
    fun theDeviceIdIsGeneratedOnceAndDoesNotChange() = runBlocking {
        val first = prefs.deviceId()
        assertTrue(first.isNotBlank() && first.length <= 64)
        assertEquals(first, prefs.deviceId())
    }

    // Dos instalaciones distintas no comparten identificador
    @Test
    fun differentInstallationsGetDifferentIds() = runBlocking {
        val other = SyncPreferencesImpl(
            PreferenceDataStoreFactory.create(scope = scope) { File(context.cacheDir, "sync-${UUID.randomUUID()}.preferences_pb") },
        )
        assertNotEquals(prefs.deviceId(), other.deviceId())
    }

    @Test
    fun theDeviceNameIsReadableAndFitsTheBackendLimit() {
        assertTrue(prefs.deviceName().length in 1..64)
    }

    @Test
    fun lastSeenStartsEmptyAndIsRemembered() = runBlocking {
        assertNull(prefs.lastPositionsSeenAt())
        prefs.setLastPositionsSeenAt(1234L)
        assertEquals(1234L, prefs.lastPositionsSeenAt())
    }
}
