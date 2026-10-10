package com.pluk.reader.ui.onboarding

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pluk.reader.domain.account.AuthError
import com.pluk.reader.domain.account.AuthRepository
import com.pluk.reader.domain.account.authError
import com.pluk.reader.domain.onboarding.PasswordCheck
import com.pluk.reader.domain.onboarding.checkPassword
import com.pluk.reader.domain.onboarding.isValidEmail
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Crear cuenta, O2 (ONB-004 a ONB-006, ONB-020, ONB-021). */
@HiltViewModel
class SignUpViewModel @Inject constructor(
    private val auth: AuthRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    enum class Next { VerifyEmail, Interests }

    data class UiState(
        val name: String = "",
        val email: String = "",
        val password: String = "",
        val passwordVisible: Boolean = false,
        val termsAccepted: Boolean = false,
        val busy: Boolean = false,
        val error: AuthError? = null,
        val next: Next? = null,
    ) {
        val passwordCheck: PasswordCheck get() = checkPassword(password)
        val emailValid: Boolean get() = isValidEmail(email)

        /** ONB-004 */
        val canSubmit: Boolean
            get() = name.isNotBlank() && emailValid && passwordCheck.isValid && termsAccepted && !busy
    }

    // ONB-008: al volver desde "Cambiar" llegan el nombre y el email, nunca la contraseña.
    private val _state = MutableStateFlow(
        UiState(
            name = savedStateHandle.get<String>(ARG_NAME).orEmpty(),
            email = savedStateHandle.get<String>(ARG_EMAIL).orEmpty(),
        ),
    )
    val state: StateFlow<UiState> = _state.asStateFlow()

    fun onName(value: String) = _state.update { it.copy(name = value, error = null) }

    fun onEmail(value: String) = _state.update { it.copy(email = value, error = null) }

    fun onPassword(value: String) = _state.update { it.copy(password = value, error = null) }

    fun togglePasswordVisible() = _state.update { it.copy(passwordVisible = !it.passwordVisible) }

    fun onTermsAccepted(value: Boolean) = _state.update { it.copy(termsAccepted = value) }

    /** ONB-006 */
    fun submit() {
        val current = _state.value
        if (!current.canSubmit) return
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            auth.createAccount(current.name, current.email, current.password).fold(
                // La contraseña no queda en memoria más de lo necesario.
                onSuccess = { _state.update { it.copy(busy = false, password = "", next = Next.VerifyEmail) } },
                onFailure = { e -> _state.update { it.copy(busy = false, error = e.authError()) } },
            )
        }
    }

    /** ONB-006: con Google no hay que verificar el email. */
    fun onGoogleToken(idToken: String) {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            auth.signInWithGoogle(idToken).fold(
                onSuccess = { _state.update { it.copy(busy = false, next = Next.Interests) } },
                onFailure = { e -> _state.update { it.copy(busy = false, error = e.authError()) } },
            )
        }
    }

    /** ONB-020: el selector de Google falló (cancelarlo no llega acá). */
    fun onGoogleFailed() = _state.update { it.copy(error = AuthError.Unknown) }

    fun onNavigated() = _state.update { it.copy(next = null) }

    companion object {
        const val ARG_NAME = "name"
        const val ARG_EMAIL = "email"
    }
}
