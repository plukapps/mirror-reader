package com.pluk.reader.data.flags

import com.google.firebase.remoteconfig.ConfigUpdate
import com.google.firebase.remoteconfig.ConfigUpdateListener
import com.google.firebase.remoteconfig.CustomSignals
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigException
import com.pluk.reader.data.plans.PlanCatalogJson
import com.pluk.reader.domain.flags.Feature
import com.pluk.reader.domain.flags.RemoteFlagSource
import com.pluk.reader.domain.plans.DefaultPlans
import com.pluk.reader.domain.plans.Plan
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.tasks.await

/**
 * Remote Config (ADR 0016, ADR 0017). Los valores por defecto de la app son los de [Feature] y [DefaultPlans]; un
 * parámetro que no existe en la consola vale su valor por defecto.
 */
@Singleton
class FirebaseRemoteFlagSource @Inject constructor() : RemoteFlagSource {
    private val config: FirebaseRemoteConfig by lazy {
        FirebaseRemoteConfig.getInstance().also { config ->
            // Todos los valores por defecto juntos: cada llamada reemplaza los anteriores (ADR 0017).
            config.setDefaultsAsync(
                Feature.entries.associate<Feature, String, Any> { it.key to it.default } +
                    (PlanCatalogJson.KEY to PlanCatalogJson.toJson(DefaultPlans.all)),
            )
        }
    }

    override suspend fun setPlan(plan: String) {
        config.setCustomSignals(CustomSignals.Builder().put(PLAN_SIGNAL, plan).build()).await()
    }

    override suspend fun activate() {
        config.activate().await()
    }

    override suspend fun fetchAndActivate() {
        config.fetchAndActivate().await()
    }

    override fun value(key: String): Boolean? =
        // Sin valor remoto ni por defecto, la fuente es STATIC: se usa el de la app. Un valor que no es booleano, también.
        config.getValue(key).takeIf { it.source != FirebaseRemoteConfig.VALUE_SOURCE_STATIC }
            ?.let { runCatching { it.asBoolean() }.getOrNull() }

    override fun plans(): List<Plan>? = PlanCatalogJson.parse(config.getString(PlanCatalogJson.KEY))

    override fun listen(onUpdate: () -> Unit): () -> Unit {
        val registration = config.addOnConfigUpdateListener(object : ConfigUpdateListener {
            override fun onUpdate(configUpdate: ConfigUpdate) = onUpdate()

            // Sin conexión el listener reintenta solo; los valores conocidos siguen valiendo.
            override fun onError(error: FirebaseRemoteConfigException) = Unit
        })
        return registration::remove
    }

    private companion object {
        /** Señal personalizada con el plan de la cuenta (`users/{uid}.plan`). */
        const val PLAN_SIGNAL = "plan"
    }
}
