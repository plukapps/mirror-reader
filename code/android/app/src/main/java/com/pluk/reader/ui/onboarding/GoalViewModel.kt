package com.pluk.reader.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pluk.reader.domain.onboarding.DailyReminder
import com.pluk.reader.domain.onboarding.OnboardingPreferences
import com.pluk.reader.domain.onboarding.ReadingGoal
import com.pluk.reader.domain.onboarding.ReminderScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Meta diaria y recordatorio, O5 (ONB-010, ONB-011). */
@HiltViewModel
class GoalViewModel @Inject constructor(
    private val preferences: OnboardingPreferences,
    private val scheduler: ReminderScheduler,
) : ViewModel() {

    data class UiState(
        val goal: ReadingGoal = ReadingGoal.Default,
        val reminder: DailyReminder = DailyReminder.Default,
        val done: Boolean = false,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    fun selectGoal(goal: ReadingGoal) = _state.update { it.copy(goal = goal) }

    fun setReminderEnabled(enabled: Boolean) = _state.update { it.copy(reminder = it.reminder.copy(enabled = enabled)) }

    fun setReminderTime(hour: Int, minute: Int) =
        _state.update { it.copy(reminder = DailyReminder(it.reminder.enabled, hour, minute)) }

    /** La pantalla pide el permiso antes si hace falta (ONB-011). */
    val needsNotificationPermission: Boolean
        get() = _state.value.reminder.enabled && !scheduler.canNotify()

    /**
     * "Terminar". Sin permiso de notificaciones la configuración se guarda igual, con el recordatorio apagado
     * (ONB-011).
     */
    fun finish() {
        val current = _state.value
        val reminder = if (current.reminder.enabled && !scheduler.canNotify()) current.reminder.copy(enabled = false) else current.reminder
        viewModelScope.launch {
            preferences.saveGoal(current.goal, reminder)
            if (reminder.enabled) scheduler.schedule(reminder, current.goal) else scheduler.cancel()
            _state.update { it.copy(reminder = reminder, done = true) }
        }
    }

    /** "Omitir": sin meta ni recordatorio. */
    fun skip() = _state.update { it.copy(done = true) }

    fun onNavigated() = _state.update { it.copy(done = false) }
}
