package com.pluk.reader.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import com.pluk.reader.domain.onboarding.WelcomePreference
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * GST-002: bienvenida ya vista, en DataStore (`welcome_seen`). No está en `LocalDataWiper.RETAINED_KEYS`, así que
 * cerrar sesión la borra y la app vuelve a la bienvenida (GST-016).
 */
class WelcomePreferenceImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : WelcomePreference {
    override val seen: Flow<Boolean> = dataStore.data.map { it[KEY] ?: false }.distinctUntilChanged()

    override suspend fun markSeen() {
        dataStore.edit { it[KEY] = true }
    }

    companion object {
        const val KEY_NAME = "welcome_seen"
        private val KEY = booleanPreferencesKey(KEY_NAME)
    }
}
