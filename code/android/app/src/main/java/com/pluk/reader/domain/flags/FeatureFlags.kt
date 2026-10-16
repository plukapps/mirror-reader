package com.pluk.reader.domain.flags

import com.pluk.reader.domain.plans.Plan
import kotlinx.coroutines.flow.StateFlow

/**
 * Funciones que se prenden y apagan sin publicar una versión nueva, para todos o por plan (ACC-011, ADR 0016).
 * [key] es el nombre del parámetro remoto; [default] vale mientras no se sabe el valor remoto.
 */
enum class Feature(val key: String, val default: Boolean) {
    /** SYN-017: sincronizar las notas. Si no se sabe, vale prendida. */
    NotesSync("notes_sync_enabled", default = true),
}

interface FeatureFlags {
    /** El valor actual de [feature] y sus cambios. */
    fun state(feature: Feature): StateFlow<Boolean>

    fun isEnabled(feature: Feature): Boolean = state(feature).value
}

/**
 * Valores remotos de las funciones y del catálogo de planes (Remote Config en `data`, ADR 0016, ADR 0017). Una sola
 * fuente por app: carga juntos todos los valores por defecto. Los tests usan un doble.
 */
interface RemoteFlagSource {
    /** Señal con el plan de la cuenta, para que las condiciones por plan elijan el valor (ADR 0016). */
    suspend fun setPlan(plan: String)

    /** Aplica lo último que se bajó. */
    suspend fun activate()

    /** Baja valores nuevos y los aplica. Falla sin conexión. */
    suspend fun fetchAndActivate()

    /** El valor aplicado de [key], o null si no hay ninguno remoto todavía. */
    fun value(key: String): Boolean?

    /** El catálogo de planes aplicado (`plans`), o null si es inválido (ACC-007, ACC-008). */
    fun plans(): List<Plan>?

    /** Avisa cuando cambian los valores en el servidor. Devuelve cómo dejar de escuchar. */
    fun listen(onUpdate: () -> Unit): () -> Unit
}
