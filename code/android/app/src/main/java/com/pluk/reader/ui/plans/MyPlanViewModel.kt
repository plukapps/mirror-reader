package com.pluk.reader.ui.plans

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pluk.reader.domain.account.QuotaSource
import com.pluk.reader.domain.profile.QuotaCache
import com.pluk.reader.domain.profile.StorageUsage
import com.pluk.reader.domain.profile.storageUsage
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Mi plan (PLN-012). En esta rebanada solo existe la variante Gratis. */
data class MyPlanUiState(
    /** Uso del espacio, o null si nunca se leyó. */
    val usage: StorageUsage? = null,
)

/** PLN-012: muestra el último espacio guardado y lo reemplaza por el del servidor cuando llega (como PRF-006). */
@HiltViewModel
class MyPlanViewModel @Inject constructor(
    private val quotaCache: QuotaCache,
    private val quotaSource: QuotaSource,
) : ViewModel() {
    val uiState: StateFlow<MyPlanUiState> = quotaCache.quota
        .map { MyPlanUiState(it?.let(::storageUsage)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), MyPlanUiState())

    init {
        viewModelScope.launch { quotaSource.current().onSuccess { quotaCache.save(it) } }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
