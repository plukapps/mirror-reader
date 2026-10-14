package com.pluk.reader.plans

import com.pluk.reader.domain.account.StorageQuota
import com.pluk.reader.domain.profile.QuotaCache
import kotlinx.coroutines.flow.MutableStateFlow

/** Caché de cuota en memoria para los tests de planes (PLN-003, PLN-009, PLN-011). */
class FakeQuotaCache(initial: StorageQuota? = null) : QuotaCache {
    override val quota = MutableStateFlow(initial)

    override suspend fun last(): StorageQuota? = quota.value

    override suspend fun save(quota: StorageQuota) {
        this.quota.value = quota
    }
}
