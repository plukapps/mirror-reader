package com.pluk.reader.data.repository

import android.os.Build
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.pluk.reader.domain.repository.SyncPreferences
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.first

class SyncPreferencesImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : SyncPreferences {
    override suspend fun deviceId(): String {
        dataStore.data.first()[DEVICE_ID]?.let { return it }
        // `edit` es atómico: si dos llamadas compiten, la segunda ve el valor de la primera y lo respeta.
        var id = ""
        dataStore.edit { prefs ->
            id = prefs[DEVICE_ID] ?: UUID.randomUUID().toString().also { prefs[DEVICE_ID] = it }
        }
        return id
    }

    override fun deviceName(): String = Build.MODEL.takeIf { it.isNotBlank() } ?: "Android"

    override suspend fun lastPositionsSeenAt(): Long? = dataStore.data.first()[LAST_POSITIONS_SEEN_AT]

    override suspend fun setLastPositionsSeenAt(value: Long) {
        dataStore.edit { it[LAST_POSITIONS_SEEN_AT] = value }
    }

    private companion object {
        val DEVICE_ID = stringPreferencesKey("sync_device_id")
        val LAST_POSITIONS_SEEN_AT = longPreferencesKey("sync_last_positions_seen_at")
    }
}
