package com.pluk.reader.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pluk.reader.domain.account.AuthError
import com.pluk.reader.domain.account.AuthRepository
import com.pluk.reader.domain.account.authError
import com.pluk.reader.domain.onboarding.PasswordCheck
import com.pluk.reader.domain.onboarding.canSaveNewPassword
import com.pluk.reader.domain.onboarding.checkPassword
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Cambiar contraseña desde Perfil (PRF-020). */
@HiltViewModel
class ChangePasswordViewModel @Inject constructor(
    private val auth: AuthRepository,
) : ViewModel() {

    data class UiState(
        val current: String = "",
        val new: String = "",
        val confirm: String = "",
        val currentVisible: Boolean = false,
        val newVisible: Boolean = false,
        val busy: Boolean = false,
        /** La contraseña actual no es correcta: el error va bajo ese campo. */
        val currentWrong: Boolean = false,
        /** Otro error (sin conexión, demasiados intentos, ...), como en ONB-015. */
        val error: AuthError? = null,
        /** La contraseña cambió: la pantalla vuelve a Perfil con el aviso. */
        val done: Boolean = false,
    ) {
        val newCheck: PasswordCheck get() = checkPassword(new)
        val mismatch: Boolean get() = confirm.isNotEmpty() && confirm != new
        val sameAsCurrent: Boolean get() = new.isNotEmpty() && new == current
        val canSave: Boolean get() = !busy && canSaveNewPassword(current, new, confirm)
    }

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    fun onCurrent(value: String) = _state.update { it.copy(current = value, currentWrong = false, error = null) }

    fun onNew(value: String) = _state.update { it.copy(new = value, error = null) }

    fun onConfirm(value: String) = _state.update { it.copy(confirm = value, error = null) }

    fun toggleCurrentVisible() = _state.update { it.copy(currentVisible = !it.currentVisible) }

    fun toggleNewVisible() = _state.update { it.copy(newVisible = !it.newVisible) }

    fun save() {
        val current = _state.value
        if (!current.canSave) return
        _state.update { it.copy(busy = true, currentWrong = false, error = null) }
        viewModelScope.launch {
            val error = auth.changePassword(current.current, current.new).exceptionOrNull()?.authError()
            _state.update {
                when (error) {
                    null -> it.copy(busy = false, current = "", new = "", confirm = "", done = true)
                    AuthError.InvalidCredentials -> it.copy(busy = false, currentWrong = true)
                    else -> it.copy(busy = false, error = error)
                }
            }
        }
    }
}
