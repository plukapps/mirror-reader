package com.pluk.reader.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import com.pluk.reader.domain.remote.NoteSyncCursor
import javax.inject.Inject
import kotlinx.coroutines.flow.first

/** Cursor de las notas en DataStore (SYN-016). Cerrar sesión lo borra con el resto (no está en `RETAINED_KEYS`). */
class NoteSyncCursorImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : NoteSyncCursor {
    override suspend fun lastSeenAt(): Long? = dataStore.data.first()[LAST_NOTES_SEEN_AT]

    override suspend fun setLastSeenAt(value: Long) {
        dataStore.edit { it[LAST_NOTES_SEEN_AT] = value }
    }

    private companion object {
        val LAST_NOTES_SEEN_AT = longPreferencesKey("sync_last_notes_seen_at")
    }
}
