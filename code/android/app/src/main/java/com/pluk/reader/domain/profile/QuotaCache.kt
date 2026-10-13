package com.pluk.reader.domain.profile

import com.pluk.reader.domain.account.StorageQuota

/** Último espacio leído, para mostrarlo sin conexión o mientras llega el nuevo (PRF-006). */
interface QuotaCache {
    suspend fun last(): StorageQuota?

    suspend fun save(quota: StorageQuota)
}
