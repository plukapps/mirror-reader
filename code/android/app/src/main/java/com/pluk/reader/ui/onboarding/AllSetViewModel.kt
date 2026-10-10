package com.pluk.reader.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pluk.reader.domain.account.AccountRepository
import com.pluk.reader.domain.onboarding.DailyReminder
import com.pluk.reader.domain.onboarding.OnboardingPreferences
import com.pluk.reader.domain.onboarding.ReadingGoal
import com.pluk.reader.domain.onboarding.firstName
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** "Ya estás adentro", O6 (ONB-013). */
@HiltViewModel
class AllSetViewModel @Inject constructor(
    accounts: AccountRepository,
    preferences: OnboardingPreferences,
) : ViewModel() {

    data class UiState(val name: String? = null, val goal: ReadingGoal? = null, val reminder: DailyReminder? = null)

    val state: StateFlow<UiState> = combine(accounts.user, preferences.goal, preferences.reminder) { user, goal, reminder ->
        UiState(firstName(user?.displayName), goal, reminder)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState())
}
