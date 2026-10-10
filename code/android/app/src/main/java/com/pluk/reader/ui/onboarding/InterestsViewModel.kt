package com.pluk.reader.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pluk.reader.domain.onboarding.Genre
import com.pluk.reader.domain.onboarding.OnboardingPreferences
import com.pluk.reader.domain.onboarding.canContinueInterests
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Intereses, O4 (ONB-009). */
@HiltViewModel
class InterestsViewModel @Inject constructor(
    private val preferences: OnboardingPreferences,
) : ViewModel() {

    data class UiState(val selected: Set<Genre> = emptySet(), val done: Boolean = false) {
        val canContinue: Boolean get() = canContinueInterests(selected)
    }

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    fun toggle(genre: Genre) = _state.update {
        it.copy(selected = if (genre in it.selected) it.selected - genre else it.selected + genre)
    }

    fun continueOn() {
        val selected = _state.value.selected
        if (!canContinueInterests(selected)) return
        viewModelScope.launch {
            preferences.saveInterests(selected)
            _state.update { it.copy(done = true) }
        }
    }

    /** "Omitir": sigue sin guardar. */
    fun skip() = _state.update { it.copy(done = true) }

    fun onNavigated() = _state.update { it.copy(done = false) }
}
