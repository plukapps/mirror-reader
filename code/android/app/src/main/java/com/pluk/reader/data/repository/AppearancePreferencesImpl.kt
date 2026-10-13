package com.pluk.reader.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.pluk.reader.domain.appearance.Appearance
import com.pluk.reader.domain.appearance.AppearancePreferences
import com.pluk.reader.domain.appearance.DEFAULT_APPEARANCE
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** PRF-021: apariencia en DataStore (clave `appearance`). Es del dispositivo: sobrevive al cierre de sesión. */
class AppearancePreferencesImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : AppearancePreferences {
    override val appearance: Flow<Appearance> = dataStore.data
        .map { prefs -> runCatching { Appearance.valueOf(prefs[KEY] ?: "") }.getOrDefault(DEFAULT_APPEARANCE) }
        .distinctUntilChanged()

    override suspend fun setAppearance(appearance: Appearance) {
        dataStore.edit { it[KEY] = appearance.name }
    }

    companion object {
        const val KEY_NAME = "appearance"
        private val KEY = stringPreferencesKey(KEY_NAME)
    }
}
