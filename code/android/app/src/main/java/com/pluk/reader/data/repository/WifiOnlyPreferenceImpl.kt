package com.pluk.reader.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import com.pluk.reader.domain.sync.WifiOnlyPreference
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** PRF-023: "Sincronizar solo con Wi-Fi" en DataStore (`sync_wifi_only`). Es del dispositivo: sobrevive al cierre de sesión. */
class WifiOnlyPreferenceImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : WifiOnlyPreference {
    override val wifiOnly: Flow<Boolean> = dataStore.data.map { it[KEY] ?: false }.distinctUntilChanged()

    override suspend fun setWifiOnly(enabled: Boolean) {
        dataStore.edit { it[KEY] = enabled }
    }

    companion object {
        const val KEY_NAME = "sync_wifi_only"
        private val KEY = booleanPreferencesKey(KEY_NAME)
    }
}
