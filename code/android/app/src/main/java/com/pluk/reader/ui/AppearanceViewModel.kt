package com.pluk.reader.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pluk.reader.domain.appearance.Appearance
import com.pluk.reader.domain.appearance.AppearancePreferences
import com.pluk.reader.domain.appearance.DEFAULT_APPEARANCE
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/** Apariencia elegida, para que la raíz provea la paleta (PRF-021). */
@HiltViewModel
class AppearanceViewModel @Inject constructor(preferences: AppearancePreferences) : ViewModel() {
    val appearance: StateFlow<Appearance> =
        preferences.appearance.stateIn(viewModelScope, SharingStarted.Eagerly, DEFAULT_APPEARANCE)
}
