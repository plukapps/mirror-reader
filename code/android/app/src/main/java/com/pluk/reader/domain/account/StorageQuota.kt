package com.pluk.reader.domain.account

/** Espacio del plan del usuario en la nube, en bytes (ACC-002, ACC-003). [plan] es el id del plan (`free`). */
data class StorageQuota(val usedBytes: Long, val quotaBytes: Long, val plan: String = FREE_PLAN) {
    val freeBytes: Long get() = (quotaBytes - usedBytes).coerceAtLeast(0)
    val isFreePlan: Boolean get() = plan == FREE_PLAN

    companion object {
        /** Id del plan gratuito, el mismo que escribe el backend en `users/{uid}.plan`. */
        const val FREE_PLAN = "free"
    }
}

interface QuotaSource {
    /** Cuota y uso actuales. Falla con `RemoteUnavailableException` si no se pueden leer. */
    suspend fun current(): Result<StorageQuota>
}
