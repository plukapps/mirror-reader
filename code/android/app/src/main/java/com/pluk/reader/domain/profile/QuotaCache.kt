package com.pluk.reader.domain.profile

import com.pluk.reader.domain.account.StorageQuota
import kotlinx.coroutines.flow.Flow

/** Último espacio leído, para mostrarlo sin conexión o mientras llega el nuevo (PRF-006). Lo actualizan Perfil, Mi plan y cada pasada de subida (PLN-009). */
interface QuotaCache {
    /** El último espacio guardado, y cada vez que cambia (PLN-003, PLN-011). Null si nunca se leyó. */
    val quota: Flow<StorageQuota?>

    suspend fun last(): StorageQuota?

    suspend fun save(quota: StorageQuota)
}
