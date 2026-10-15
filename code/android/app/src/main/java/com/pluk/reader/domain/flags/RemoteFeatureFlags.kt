package com.pluk.reader.domain.flags

import com.pluk.reader.domain.ApplicationScope
import com.pluk.reader.domain.account.StorageQuota
import com.pluk.reader.domain.plans.PlanCatalog
import com.pluk.reader.domain.profile.QuotaCache
import com.pluk.reader.domain.usecase.CloudListener
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Funciones prendidas o apagadas según la configuración remota (ACC-011, SYN-017, ADR 0016).
 *
 * Arranca con los valores por defecto. Con la app a la vista: manda el plan de la cuenta, aplica lo último bajado,
 * pide lo nuevo y escucha los cambios del servidor, así apagar o prender llega en segundos.
 */
@Singleton
class RemoteFeatureFlags @Inject constructor(
    private val source: RemoteFlagSource,
    private val quota: QuotaCache,
    @ApplicationScope private val scope: CoroutineScope,
) : FeatureFlags, CloudListener {
    private val states = Feature.entries.associateWith { MutableStateFlow(it.default) }
    private var stopUpdates: (() -> Unit)? = null

    override fun state(feature: Feature): StateFlow<Boolean> = states.getValue(feature).asStateFlow()

    override fun startListening() {
        if (stopUpdates != null) return
        stopUpdates = source.listen { scope.launch { runCatching { source.activate() }; publish() } }
        scope.launch {
            runCatching { source.setPlan(quota.last()?.plan ?: StorageQuota.FREE_PLAN) }
            runCatching { source.activate() }
            publish()
            // Sin conexión quedan los últimos valores conocidos (o los de por defecto).
            runCatching { source.fetchAndActivate() }
            publish()
        }
    }

    override fun stopListening() {
        stopUpdates?.invoke()
        stopUpdates = null
    }

    private fun publish() {
        states.forEach { (feature, state) -> state.value = source.value(feature.key) ?: feature.default }
        // ACC-007, ACC-008, K-290: el catálogo de planes viaja con la misma configuración; inválido, los por defecto.
        PlanCatalog.update(source.plans())
    }
}
