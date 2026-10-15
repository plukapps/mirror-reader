package com.pluk.reader.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.pluk.reader.domain.account.StorageQuota
import com.pluk.reader.domain.profile.QuotaCache
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

class QuotaCacheImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : QuotaCache {
    override val quota: Flow<StorageQuota?> = dataStore.data.map { it.toQuota() }.distinctUntilChanged()

    override suspend fun last(): StorageQuota? = dataStore.data.first().toQuota()

    private fun Preferences.toQuota(): StorageQuota? {
        val used = this[USED_BYTES] ?: return null
        val quota = this[QUOTA_BYTES] ?: return null
        return StorageQuota(used, quota, this[PLAN] ?: StorageQuota.FREE_PLAN, this[MAX_FILE_BYTES])
    }

    override suspend fun save(quota: StorageQuota) {
        dataStore.edit {
            it[USED_BYTES] = quota.usedBytes
            it[QUOTA_BYTES] = quota.quotaBytes
            it[PLAN] = quota.plan
            val maxFileBytes = quota.maxFileBytes
            if (maxFileBytes == null) it.remove(MAX_FILE_BYTES) else it[MAX_FILE_BYTES] = maxFileBytes
        }
    }

    private companion object {
        val USED_BYTES = longPreferencesKey("profile_used_bytes")
        val QUOTA_BYTES = longPreferencesKey("profile_quota_bytes")
        val PLAN = stringPreferencesKey("profile_plan")
        val MAX_FILE_BYTES = longPreferencesKey("profile_max_file_bytes")
    }
}
