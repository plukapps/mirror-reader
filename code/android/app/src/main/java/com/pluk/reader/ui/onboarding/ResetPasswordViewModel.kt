package com.pluk.reader.ui.onboarding

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pluk.reader.domain.account.AuthError
import com.pluk.reader.domain.account.AuthRepository
import com.pluk.reader.domain.account.authError
import com.pluk.reader.domain.onboarding.PasswordCheck
import com.pluk.reader.domain.onboarding.checkPassword
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Nueva contraseña desde el enlace del email, O9 (ONB-018). */
@HiltViewModel
class ResetPasswordViewModel @Inject constructor(
    private val auth: AuthRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    enum class Next { Home, SignIn }

    data class UiState(
        val loading: Boolean = true,
        val email: String = "",
        val linkInvalid: Boolean = false,
        val password: String = "",
        val confirm: String = "",
        val passwordVisible: Boolean = false,
        val busy: Boolean = false,
        val error: AuthError? = null,
        val next: Next? = null,
    ) {
        val passwordCheck: PasswordCheck get() = checkPassword(password)
        val mismatch: Boolean get() = confirm.isNotEmpty() && confirm != password
        val canSave: Boolean get() = !loading && !linkInvalid && passwordCheck.isValid && password == confirm && !busy
    }

    private val code: String = savedStateHandle.get<String>(ARG_CODE).orEmpty()

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val email = if (code.isBlank()) null else auth.emailForResetCode(code).getOrNull()
            _state.update { it.copy(loading = false, email = email.orEmpty(), linkInvalid = email == null) }
        }
    }

    fun onPassword(value: String) = _state.update { it.copy(password = value, error = null) }

    fun onConfirm(value: String) = _state.update { it.copy(confirm = value, error = null) }

    fun togglePasswordVisible() = _state.update { it.copy(passwordVisible = !it.passwordVisible) }

    /** "Guardar e ingresar". Si la contraseña cambia pero el ingreso falla, se va a iniciar sesión. */
    fun save() {
        val current = _state.value
        if (!current.canSave) return
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            val changed = auth.confirmPasswordReset(code, current.password)
            changed.exceptionOrNull()?.let { e ->
                val error = e.authError()
                _state.update { it.copy(busy = false, error = error, linkInvalid = error == AuthError.InvalidLink) }
                return@launch
            }
            val next = if (auth.signInWithPassword(current.email, current.password).isSuccess) Next.Home else Next.SignIn
            _state.update { it.copy(busy = false, password = "", confirm = "", next = next) }
        }
    }

    fun onNavigated() = _state.update { it.copy(next = null) }

    companion object {
        const val ARG_CODE = "oobCode"
    }
}
