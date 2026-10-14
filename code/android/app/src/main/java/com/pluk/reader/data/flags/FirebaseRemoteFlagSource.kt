package com.pluk.reader.data.flags

import com.google.firebase.remoteconfig.ConfigUpdate
import com.google.firebase.remoteconfig.ConfigUpdateListener
import com.google.firebase.remoteconfig.CustomSignals
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigException
import com.pluk.reader.domain.flags.Feature
import com.pluk.reader.domain.flags.RemoteFlagSource
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.tasks.await

/**
 * Remote Config (ADR 0016). Los valores por defecto de la app son los de [Feature]; un parámetro que no existe en la
 * consola vale su valor por defecto.
 */
@Singleton
class FirebaseRemoteFlagSource @Inject constructor() : RemoteFlagSource {
    private val config: FirebaseRemoteConfig by lazy {
        FirebaseRemoteConfig.getInstance().also { config ->
            config.setDefaultsAsync(Feature.entries.associate { it.key to it.default })
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
