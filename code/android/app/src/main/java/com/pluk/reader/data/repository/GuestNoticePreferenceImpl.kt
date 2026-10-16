package com.pluk.reader.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import com.pluk.reader.domain.account.GuestNoticePreference
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * GST-010: cuándo se cerró el aviso "solo en este dispositivo", en DataStore (`guest_notice_dismissed_at`). No está en
 * `LocalDataWiper.RETAINED_KEYS`, así que se borra al cerrar sesión (ADR 0019).
 */
class GuestNoticePreferenceImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : GuestNoticePreference {
    override val dismissedAt: Flow<Long?> = dataStore.data.map { it[KEY] }.distinctUntilChanged()

    override suspend fun dismiss(at: Long) {
        dataStore.edit { it[KEY] = at }
    }

    companion object {
        const val KEY_NAME = "guest_notice_dismissed_at"
        private val KEY = longPreferencesKey(KEY_NAME)
    }
}
