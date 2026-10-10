package com.pluk.reader.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pluk.reader.domain.account.AuthError
import com.pluk.reader.domain.account.AuthRepository
import com.pluk.reader.domain.account.authError
import com.pluk.reader.domain.onboarding.isValidEmail
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Iniciar sesión, O7 (ONB-014 a ONB-016, ONB-020, ONB-021). */
@HiltViewModel
class SignInViewModel @Inject constructor(
    private val auth: AuthRepository,
) : ViewModel() {

    enum class Next { Home, VerifyEmail }

    data class UiState(
        val email: String = "",
        val password: String = "",
        val passwordVisible: Boolean = false,
        val busy: Boolean = false,
        val error: AuthError? = null,
        val next: Next? = null,
        /** Si salió el email de verificación al ingresar (ONB-016): O3 arranca esperando para reenviar. */
        val verificationSent: Boolean = false,
    ) {
        val canSubmit: Boolean get() = isValidEmail(email) && password.isNotEmpty() && !busy
    }

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    fun onEmail(value: String) = _state.update { it.copy(email = value, error = null) }

    fun onPassword(value: String) = _state.update { it.copy(password = value, error = null) }

    fun togglePasswordVisible() = _state.update { it.copy(passwordVisible = !it.passwordVisible) }

    fun submit() {
        val current = _state.value
        if (!current.canSubmit) return
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            auth.signInWithPassword(current.email, current.password).fold(
                // ONB-016
                onSuccess = { user ->
                    // ONB-016: la cuenta pudo crearse en otro lado sin verificar: el email sale ahora.
                    val sent = user.needsEmailVerification && auth.sendEmailVerification().isSuccess
                    val next = if (user.needsEmailVerification) Next.VerifyEmail else Next.Home
                    _state.update { it.copy(busy = false, password = "", next = next, verificationSent = sent) }
                },
                onFailure = { e -> _state.update { it.copy(busy = false, error = e.authError()) } },
            )
        }
    }

    fun onGoogleToken(idToken: String) {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            auth.signInWithGoogle(idToken).fold(
                onSuccess = { _state.update { it.copy(busy = false, next = Next.Home) } },
                onFailure = { e -> _state.update { it.copy(busy = false, error = e.authError()) } },
            )
        }
    }

    fun onGoogleFailed() = _state.update { it.copy(error = AuthError.Unknown) }

    fun onNavigated() = _state.update { it.copy(next = null) }
}
