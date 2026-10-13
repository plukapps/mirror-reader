package com.pluk.reader.domain.appearance

import kotlinx.coroutines.flow.Flow

/** Apariencia de la app (PRF-021). Es de este dispositivo: no se sincroniza. */
enum class Appearance { LIGHT, DARK, SYSTEM }

/** PRF-021: por defecto, Sistema. */
val DEFAULT_APPEARANCE = Appearance.SYSTEM

/** PRF-021: modo efectivo. Sistema sigue el modo del dispositivo. */
fun isDarkMode(appearance: Appearance, systemDark: Boolean): Boolean = when (appearance) {
    Appearance.LIGHT -> false
    Appearance.DARK -> true
    Appearance.SYSTEM -> systemDark
}

/** Preferencia de apariencia guardada en este dispositivo (PRF-021). */
interface AppearancePreferences {
    val appearance: Flow<Appearance>

    suspend fun setAppearance(appearance: Appearance)
}
