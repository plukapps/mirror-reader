package com.pluk.reader.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pluk.reader.domain.account.AccountRepository
import com.pluk.reader.domain.account.AccountUser
import com.pluk.reader.domain.account.QuotaSource
import com.pluk.reader.domain.account.SignInMethod
import com.pluk.reader.domain.account.StorageQuota
import com.pluk.reader.domain.profile.QuotaCache
import com.pluk.reader.domain.profile.formatStorage
import com.pluk.reader.domain.profile.storageUnitFor
import com.pluk.reader.domain.repository.SyncPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Espacio listo para mostrar (PRF-006): tamaños en la unidad del total y proporción usada entre 0 y 1. */
data class StorageUi(
    val used: String,
    val total: String,
    val free: String,
    val fraction: Float,
    val isFreePlan: Boolean,
)

data class ProfileUiState(
    /** Nombre de la cuenta, o null si no tiene: entonces la cabecera muestra solo el email (PRF-005). */
    val name: String? = null,
    val email: String? = null,
    val initial: String = "",
    val canChangePassword: Boolean = false,
    /** Null mientras no hay ningún valor (ni leído ni guardado). */
    val storage: StorageUi? = null,
    /** Verdadero si la lectura falló y no había valor guardado. */
    val storageFailed: Boolean = false,
    val deviceName: String = "",
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    account: AccountRepository,
    private val quotaSource: QuotaSource,
    private val quotaCache: QuotaCache,
    syncPreferences: SyncPreferences,
) : ViewModel() {
    private val quota = MutableStateFlow<StorageQuota?>(null)
    private val quotaFailed = MutableStateFlow(false)
    private val deviceName = syncPreferences.deviceName()

    val uiState: StateFlow<ProfileUiState> = combine(account.user, quota, quotaFailed) { user, quota, failed ->
        ProfileUiState(
            name = user?.displayName,
            email = user?.email,
            initial = user.initial(),
            canChangePassword = user?.signInMethod == SignInMethod.PASSWORD,
            storage = quota?.toUi(),
            storageFailed = failed && quota == null,
            deviceName = deviceName,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), ProfileUiState(deviceName = deviceName))

    init {
        refreshQuota()
    }

    /** PRF-006: muestra el último valor guardado y lo reemplaza por el del servidor cuando llega. */
    fun refreshQuota() {
        viewModelScope.launch {
            if (quota.value == null) quota.value = quotaCache.last()
            quotaSource.current()
                .onSuccess {
                    quota.value = it
                    quotaFailed.value = false
                    quotaCache.save(it)
                }
                .onFailure { quotaFailed.value = true }
        }
    }

    private fun AccountUser?.initial(): String =
        (this?.displayName ?: this?.email)?.trim()?.firstOrNull()?.uppercase() ?: ""

    private fun StorageQuota.toUi(): StorageUi {
        val unit = storageUnitFor(quotaBytes)
        return StorageUi(
            used = formatStorage(usedBytes, unit),
            total = formatStorage(quotaBytes, unit),
            free = formatStorage(freeBytes, unit),
            fraction = if (quotaBytes <= 0) 0f else (usedBytes.toFloat() / quotaBytes).coerceIn(0f, 1f),
            isFreePlan = isFreePlan,
        )
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
