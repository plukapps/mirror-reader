package com.pluk.reader.domain.onboarding

import com.pluk.reader.domain.account.AccountUser
import kotlinx.coroutines.flow.Flow

/** Pantalla con la que abre la app (GST-003, reemplaza ONB-019). */
enum class StartDestination { Welcome, VerifyEmail, Home }

/**
 * GST-003: con sesión decide la sesión (GST-004: no depende de la bienvenida); sin sesión, Inicio si en este
 * dispositivo ya se pasó por la bienvenida ("Empezar a leer", GST-002), y la bienvenida si no.
 */
fun startDestination(user: AccountUser?, welcomeSeen: Boolean): StartDestination = when {
    user == null -> if (welcomeSeen) StartDestination.Home else StartDestination.Welcome
    user.needsEmailVerification -> StartDestination.VerifyEmail
    else -> StartDestination.Home
}

/**
 * GST-002: en este dispositivo ya se pasó por la bienvenida sin cuenta. Se borra al cerrar sesión (GST-016,
 * ADR 0019), así que se vuelve a la bienvenida.
 */
interface WelcomePreference {
    val seen: Flow<Boolean>

    suspend fun markSeen()
}

/** Géneros de O4, en el orden del diseño (ONB-009). */
enum class Genre { Philosophy, Classics, Poetry, SciFi, History, Essays, Mystery, Romance, Biography, Horror, Science, Travel, Drama }

const val MIN_INTERESTS = 3

fun canContinueInterests(selected: Set<Genre>): Boolean = selected.size >= MIN_INTERESTS

/** Opciones de O5 (ONB-010). */
enum class ReadingGoal(val minutes: Int) {
    Casual(10), Regular(20), Serious(30), Devoted(60);

    companion object {
        val Default = Regular
    }
}

/** Recordatorio diario (ONB-010, ONB-012). Hora local del dispositivo. */
data class DailyReminder(val enabled: Boolean, val hour: Int, val minute: Int) {
    init {
        require(hour in 0..23 && minute in 0..59)
    }

    companion object {
        val Default = DailyReminder(enabled = true, hour = 21, minute = 30)
    }
}

/** Lo elegido en el onboarding. Vive en el dispositivo (ADR 0014). */
interface OnboardingPreferences {
    val interests: Flow<Set<Genre>>
    val goal: Flow<ReadingGoal?>
    val reminder: Flow<DailyReminder?>

    suspend fun saveInterests(genres: Set<Genre>)

    suspend fun saveGoal(goal: ReadingGoal, reminder: DailyReminder)
}

/** Programa la notificación diaria (ONB-012). La implementación vive en `data`. */
interface ReminderScheduler {
    /** Programa (o reprograma) la notificación diaria con la meta en el texto. */
    fun schedule(reminder: DailyReminder, goal: ReadingGoal?)

    fun cancel()

    /** Si el sistema deja mostrar notificaciones (ONB-011). */
    fun canNotify(): Boolean
}
