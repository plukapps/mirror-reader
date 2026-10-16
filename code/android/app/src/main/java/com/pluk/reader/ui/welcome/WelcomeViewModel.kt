package com.pluk.reader.ui.welcome

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pluk.reader.domain.onboarding.WelcomePreference
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch

/** GST-002: "Empezar a leer" deja registrado que ya se pasó por la bienvenida y recién después sigue. */
@HiltViewModel
class WelcomeViewModel @Inject constructor(
    private val welcome: WelcomePreference,
) : ViewModel() {
    /** Guarda antes de seguir: al navegar se cierra la bienvenida y, con ella, este ViewModel. */
    fun startReading(then: () -> Unit) {
        viewModelScope.launch {
            welcome.markSeen()
            then()
        }
    }
}
