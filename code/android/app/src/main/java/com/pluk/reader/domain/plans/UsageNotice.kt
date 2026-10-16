package com.pluk.reader.domain.plans

import com.pluk.reader.domain.account.StorageQuota
import kotlinx.coroutines.flow.Flow

/** Desde qué proporción de uso aparece el aviso suave (PLN-011). */
const val USAGE_NOTICE_THRESHOLD = 0.8

/** PLN-011: el aviso se muestra con el uso entre el 80 % y el 100 % (sin llenar), si no se descartó. */
fun shouldShowUsageNotice(quota: StorageQuota?, dismissed: Boolean): Boolean {
    if (quota == null || dismissed || quota.quotaBytes <= 0) return false
    return quota.usedBytes >= quota.quotaBytes * USAGE_NOTICE_THRESHOLD && quota.usedBytes < quota.quotaBytes
}

/** PLN-011: el aviso del 80 % se descarta para siempre en esta cuenta y este dispositivo (se borra al cerrar sesión). */
interface UsageNoticePreference {
    val dismissed: Flow<Boolean>

    suspend fun dismiss()
}
