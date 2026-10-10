package com.pluk.reader.ui.onboarding

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pluk.reader.domain.account.AuthError
import com.pluk.reader.domain.account.AuthRepository
import com.pluk.reader.domain.account.authError
import com.pluk.reader.domain.onboarding.isValidEmail
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Olvidé mi contraseña, O8 (ONB-017). */
@HiltViewModel
class ForgotPasswordViewModel @Inject constructor(
    private val auth: AuthRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    data class UiState(
        val email: String = "",
        val busy: Boolean = false,
        val sent: Boolean = false,
        val resendSeconds: Int = 0,
        val error: AuthError? = null,
    ) {
        val canSend: Boolean get() = isValidEmail(email) && !busy && resendSeconds == 0
    }

    private val _state = MutableStateFlow(UiState(email = savedStateHandle.get<String>(ARG_EMAIL).orEmpty()))
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var cooldown: Job? = null

    fun onEmail(value: String) = _state.update { it.copy(email = value, error = null) }

    /** Enviar y reenviar. */
    fun send() {
        val current = _state.value
        if (!current.canSend) return
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            auth.sendPasswordReset(current.email).fold(
                onSuccess = {
                    _state.update { it.copy(busy = false, sent = true) }
                    cooldown?.cancel()
                    cooldown = viewModelScope.countdown(RESEND_SECONDS) { left -> _state.update { it.copy(resendSeconds = left) } }
                },
                onFailure = { e -> _state.update { it.copy(busy = false, error = e.authError()) } },
            )
        }
    }

    companion object {
        const val ARG_EMAIL = "email"
        const val RESEND_SECONDS = 30
    }
}
