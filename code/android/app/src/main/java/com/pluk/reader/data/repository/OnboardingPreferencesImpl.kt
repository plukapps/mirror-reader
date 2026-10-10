package com.pluk.reader.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.pluk.reader.domain.onboarding.DailyReminder
import com.pluk.reader.domain.onboarding.Genre
import com.pluk.reader.domain.onboarding.OnboardingPreferences
import com.pluk.reader.domain.onboarding.ReadingGoal
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/** Intereses, meta y recordatorio del onboarding, junto a las preferencias de la app (ONB-009, ONB-010, ADR 0014). */
class OnboardingPreferencesImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : OnboardingPreferences {

    override val interests: Flow<Set<Genre>> = dataStore.data.map { prefs ->
        prefs[INTERESTS].orEmpty().mapNotNull { name -> Genre.entries.find { it.name == name } }.toSet()
    }

    override val goal: Flow<ReadingGoal?> = dataStore.data.map { prefs ->
        prefs[GOAL_MINUTES]?.let { minutes -> ReadingGoal.entries.find { it.minutes == minutes } }
    }

    override val reminder: Flow<DailyReminder?> = dataStore.data.map { prefs ->
        val enabled = prefs[REMINDER_ENABLED] ?: return@map null
        val hour = prefs[REMINDER_HOUR] ?: return@map null
        val minute = prefs[REMINDER_MINUTE] ?: return@map null
        runCatching { DailyReminder(enabled, hour, minute) }.getOrNull()
    }

    override suspend fun saveInterests(genres: Set<Genre>) {
        dataStore.edit { it[INTERESTS] = genres.map(Genre::name).toSet() }
    }

    override suspend fun saveGoal(goal: ReadingGoal, reminder: DailyReminder) {
        dataStore.edit {
            it[GOAL_MINUTES] = goal.minutes
            it[REMINDER_ENABLED] = reminder.enabled
            it[REMINDER_HOUR] = reminder.hour
            it[REMINDER_MINUTE] = reminder.minute
        }
    }

    private companion object {
        val INTERESTS = stringSetPreferencesKey("onboardingInterests")
        val GOAL_MINUTES = intPreferencesKey("readingGoalMinutes")
        val REMINDER_ENABLED = booleanPreferencesKey("reminderEnabled")
        val REMINDER_HOUR = intPreferencesKey("reminderHour")
        val REMINDER_MINUTE = intPreferencesKey("reminderMinute")
    }
}
