package com.pluk.reader.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.pluk.reader.domain.account.StorageQuota
import com.pluk.reader.domain.profile.QuotaCache
import javax.inject.Inject
import kotlinx.coroutines.flow.first

class QuotaCacheImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : QuotaCache {
    override suspend fun last(): StorageQuota? {
        val prefs = dataStore.data.first()
        val used = prefs[USED_BYTES] ?: return null
        val quota = prefs[QUOTA_BYTES] ?: return null
        return StorageQuota(used, quota, prefs[PLAN] ?: StorageQuota.FREE_PLAN)
    }

    override suspend fun save(quota: StorageQuota) {
        dataStore.edit {
            it[USED_BYTES] = quota.usedBytes
            it[QUOTA_BYTES] = quota.quotaBytes
            it[PLAN] = quota.plan
        }
    }

    private companion object {
        val USED_BYTES = longPreferencesKey("profile_used_bytes")
        val QUOTA_BYTES = longPreferencesKey("profile_quota_bytes")
        val PLAN = stringPreferencesKey("profile_plan")
    }
}
