package com.pluk.reader.data

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pluk.reader.data.repository.SettingsRepositoryImpl
import com.pluk.reader.domain.model.ReaderSettings
import com.pluk.reader.domain.model.ReadingTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class SettingsRepositoryTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val file = File(context.cacheDir, "settings-${UUID.randomUUID()}.preferences_pb")
    private val dataStore = PreferenceDataStoreFactory.create(scope = scope) { file }
    private lateinit var repository: SettingsRepositoryImpl

    @Before
    fun setUp() {
        repository = SettingsRepositoryImpl(dataStore)
    }

    @After
    fun tearDown() {
        scope.cancel()
        file.delete()
    }

    // Primera ejecución: valores por defecto
    @Test
    fun defaultsWhenNothingSaved() = runBlocking {
        assertEquals(ReaderSettings(), repository.settings.first())
    }

    // RDR-002, RDR-003
    @Test
    fun settingsRoundTrip() = runBlocking {
        val wanted = ReaderSettings(scroll = true, theme = ReadingTheme.SEPIA, fontScale = 1.4, pageAnimation = false)
        repository.update { wanted }
        // La escala 1.4 debe volver exacta, sin degradarse a 1.3999999.
        assertEquals(wanted, repository.settings.first())
    }

    // RDR-001: update aplica la transformación sobre lo guardado
    @Test
    fun updateAppliesTransformationOverStoredValue() = runBlocking {
        repository.update { it.toggleScroll() }
        repository.update { it.nextTheme() }
        assertEquals(ReaderSettings(scroll = true, theme = ReadingTheme.DARK), repository.settings.first())
    }

    // Un valor de tema desconocido no debe romper la app
    @Test
    fun unknownThemeFallsBackToDefault() = runBlocking {
        dataStore.edit { it[stringPreferencesKey("theme")] = "NEON" }
        assertEquals(ReadingTheme.LIGHT, repository.settings.first().theme)
    }
}
