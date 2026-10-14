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
    val selected: PlanId = PlanId.READER,
    val tableOpen: Boolean = false,
    /** Uso de la cuenta, o null si nunca se leyó la cuota: la fila se oculta (PLN-003). */
    val usage: StorageUsage? = null,
    /** Espacio de Gratis: la cuota de la cuenta (PLN-001). */
    val freeCloud: String = formatCloud(PlanCatalog.FREE_QUOTA_BYTES),
    /** Gratis lleva "Tu plan" (PLN-005). Sin cuota conocida, la cuenta es gratuita: no hay otro plan todavía. */
    val isOnFreePlan: Boolean = true,
) {
    val cta: PlanCta get() = ctaFor(selected, billing)

    /** PLN-005: la tarjeta elegida va en amarillo; Gratis nunca. */
    fun isHighlighted(plan: PlanId): Boolean = plan != PlanId.FREE && plan == selected
}

/** "15 MB", "5 GB": el espacio de un plan en su propia unidad (PRF-006). */
fun formatCloud(bytes: Long): String = formatStorage(bytes, storageUnitFor(bytes))

/** P01 Planes (PLN-002 a PLN-006). Sin compras: el botón y "Restaurar compras" avisan (PRF-003). */
@HiltViewModel
class PlansViewModel @Inject constructor(quotaCache: QuotaCache) : ViewModel() {
    private val choice = MutableStateFlow(PlansUiState())

    val uiState: StateFlow<PlansUiState> = combine(choice, quotaCache.quota) { choice, quota ->
        choice.copy(
            usage = quota?.let(::storageUsage),
            freeCloud = formatCloud(PlanCatalog.cloudBytes(PlanCatalog.free, quota)),
            isOnFreePlan = quota?.isFreePlan ?: true,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), PlansUiState())

    fun onBilling(billing: Billing) {
        choice.value = choice.value.copy(billing = billing)
    }

    /** Solo Lector y Biblioteca se eligen (PLN-005). */
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
