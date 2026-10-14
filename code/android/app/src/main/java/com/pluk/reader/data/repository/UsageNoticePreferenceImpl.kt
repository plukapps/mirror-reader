package com.pluk.reader.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import com.pluk.reader.domain.plans.UsageNoticePreference
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * PLN-011: aviso del 80 % descartado, en DataStore (`plans_usage_notice_dismissed`). Es de la cuenta: no está en
 * `LocalDataWiper.RETAINED_KEYS`, así que se borra al cerrar sesión.
 */
class UsageNoticePreferenceImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : UsageNoticePreference {
    override val dismissed: Flow<Boolean> = dataStore.data.map { it[KEY] ?: false }.distinctUntilChanged()

    override suspend fun dismiss() {
        dataStore.edit { it[KEY] = true }
    }

    companion object {
        const val KEY_NAME = "plans_usage_notice_dismissed"
        private val KEY = booleanPreferencesKey(KEY_NAME)
    }
}
