package com.pluk.reader.ui.plans

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pluk.reader.domain.account.QuotaSource
import com.pluk.reader.domain.plans.DefaultPlans
import com.pluk.reader.domain.plans.PlanCatalog
import com.pluk.reader.domain.plans.PlanId
import com.pluk.reader.domain.profile.QuotaCache
import com.pluk.reader.domain.profile.StorageUsage
import com.pluk.reader.domain.profile.storageUsage
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Mi plan (PLN-012 en Free, PLN-017 en Plus y Pro). */
data class MyPlanUiState(
    /** Uso del espacio, o null si nunca se leyó. */
    val usage: StorageUsage? = null,
    /** Plan de la cuenta (PLN-014); sin dato, Free. */
    val plan: PlanId = PlanId.FREE,
    /** Máximo por archivo de la cuenta: el del servidor si se conoce (ACC-008, ACC-010). */
    val maxFileBytes: Long = DefaultPlans.free.maxFileBytes,
)

/** PLN-012: muestra el último espacio guardado y lo reemplaza por el del servidor cuando llega (como PRF-006). */
@HiltViewModel
class MyPlanViewModel @Inject constructor(
    private val quotaCache: QuotaCache,
    private val quotaSource: QuotaSource,
) : ViewModel() {
    // ACC-007, K-290: también cambia si llega otro catálogo de planes (el máximo sin dato del servidor).
    val uiState: StateFlow<MyPlanUiState> = combine(quotaCache.quota, PlanCatalog.plans) { quota, _ ->
        MyPlanUiState(quota?.let(::storageUsage), PlanCatalog.planOf(quota), PlanCatalog.maxFileBytes(quota))
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), MyPlanUiState())

    init {
        viewModelScope.launch { quotaSource.current().onSuccess { quotaCache.save(it) } }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
