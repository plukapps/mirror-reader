package com.pluk.reader.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.pluk.reader.domain.model.ReaderSettings
import com.pluk.reader.domain.model.ReadingTheme
import com.pluk.reader.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import kotlin.math.roundToInt

class SettingsRepositoryImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : SettingsRepository {

    override val settings: Flow<ReaderSettings> = dataStore.data.map { it.toSettings() }

    override suspend fun update(transform: (ReaderSettings) -> ReaderSettings) {
        dataStore.edit { prefs ->
            val updated = transform(prefs.toSettings())
            prefs[SCROLL] = updated.scroll
            prefs[THEME] = updated.theme.name
            prefs[PAGE_ANIMATION] = updated.pageAnimation
            // En décimas enteras: un Float degradaría 1.4 a 1.3999999.
            prefs[FONT_TENTHS] = (updated.fontScale * 10).roundToInt()
        }
    }

    private fun Preferences.toSettings(): ReaderSettings {
        val defaults = ReaderSettings()
        return ReaderSettings(
            scroll = this[SCROLL] ?: defaults.scroll,
            theme = runCatching { ReadingTheme.valueOf(this[THEME] ?: "") }.getOrDefault(defaults.theme),
            fontScale = (this[FONT_TENTHS] ?: (defaults.fontScale * 10).roundToInt()) / 10.0,
            pageAnimation = this[PAGE_ANIMATION] ?: defaults.pageAnimation,
        )
    }

    private companion object {
        val SCROLL = booleanPreferencesKey("scroll")
        val THEME = stringPreferencesKey("theme")
        val FONT_TENTHS = intPreferencesKey("fontTenths")
        val PAGE_ANIMATION = booleanPreferencesKey("pageAnimation")
    }
}
