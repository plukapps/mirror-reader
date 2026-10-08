package com.pluk.reader.domain.account

/** Espacio del plan del usuario en la nube, en bytes (ACC-002, ACC-003). */
data class StorageQuota(val usedBytes: Long, val quotaBytes: Long) {
    val freeBytes: Long get() = (quotaBytes - usedBytes).coerceAtLeast(0)
}

interface QuotaSource {
    /** Cuota y uso actuales. Falla con `RemoteUnavailableException` si no se pueden leer. */
    suspend fun current(): Result<StorageQuota>
}
