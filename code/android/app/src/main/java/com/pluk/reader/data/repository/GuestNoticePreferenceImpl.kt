package com.pluk.reader.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import com.pluk.reader.domain.account.GuestNoticePreference
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * GST-010: aviso "solo en este dispositivo" descartado, en DataStore (`guest_notice_dismissed`). No está en
 * `LocalDataWiper.RETAINED_KEYS`, así que se borra al cerrar sesión (ADR 0019).
 */
class GuestNoticePreferenceImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : GuestNoticePreference {
    override val dismissed: Flow<Boolean> = dataStore.data.map { it[KEY] ?: false }.distinctUntilChanged()

    override suspend fun dismiss() {
        dataStore.edit { it[KEY] = true }
    }

    companion object {
        const val KEY_NAME = "guest_notice_dismissed"
        private val KEY = booleanPreferencesKey(KEY_NAME)
    }
}
