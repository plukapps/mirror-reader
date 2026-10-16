package com.pluk.reader.ui.plans

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pluk.reader.domain.plans.Billing
import com.pluk.reader.domain.plans.PlanCatalog
import com.pluk.reader.domain.plans.PlanCta
import com.pluk.reader.domain.plans.PlanId
import com.pluk.reader.domain.plans.ctaFor
import com.pluk.reader.domain.profile.QuotaCache
import com.pluk.reader.domain.profile.StorageUsage
import com.pluk.reader.domain.profile.formatStorage
import com.pluk.reader.domain.profile.storageUnitFor
import com.pluk.reader.domain.profile.storageUsage
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class PlansUiState(
    /** PLN-004: arranca en Mensual. */
    val billing: Billing = Billing.MONTHLY,
    /** Plan pago elegido con el radio (PLN-005). */
    val selected: PlanId = PlanId.PLUS,
    val tableOpen: Boolean = false,
    /** Uso de la cuenta, o null si nunca se leyó la cuota: la fila se oculta (PLN-003). */
    val usage: StorageUsage? = null,
    /** Espacio de Free: la cuota de la cuenta (PLN-001). */
    val freeCloud: String = formatCloud(PlanCatalog.of(PlanId.FREE).cloudBytes),
    /** Plan de la cuenta, que lleva "Tu plan" (PLN-005, PLN-017). Sin cuota conocida, Free. */
    val accountPlan: PlanId = PlanId.FREE,
) {
    val cta: PlanCta get() = ctaFor(selected, billing)

    /** PLN-005: la tarjeta elegida va en amarillo; Free nunca. */
    fun isHighlighted(plan: PlanId): Boolean = plan != PlanId.FREE && plan == selected
}

/** "15 MB", "5 GB": el espacio de un plan en su propia unidad (PRF-006). */
fun formatCloud(bytes: Long): String = formatStorage(bytes, storageUnitFor(bytes))

/** P01 Planes (PLN-002 a PLN-006). Sin compras: el botón y "Restaurar compras" avisan (PRF-003). */
@HiltViewModel
class PlansViewModel @Inject constructor(quotaCache: QuotaCache) : ViewModel() {
    private val choice = MutableStateFlow(PlansUiState())

    // ACC-007, K-290: también cambia si llega otro catálogo de planes.
    val uiState: StateFlow<PlansUiState> = combine(choice, quotaCache.quota, PlanCatalog.plans) { choice, quota, plans ->
        choice.copy(
            usage = quota?.let(::storageUsage),
            freeCloud = formatCloud(PlanCatalog.cloudBytes(plans.first { it.id == PlanId.FREE }, quota)),
            accountPlan = PlanCatalog.planOf(quota),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), PlansUiState())

    fun onBilling(billing: Billing) {
        choice.value = choice.value.copy(billing = billing)
    }

    /** Solo Plus y Pro se eligen (PLN-005). */
    fun onSelect(plan: PlanId) {
        if (plan == PlanId.FREE) return
        choice.value = choice.value.copy(selected = plan)
    }

    fun onToggleTable() {
        choice.value = choice.value.copy(tableOpen = !choice.value.tableOpen)
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
