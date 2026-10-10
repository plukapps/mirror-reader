package com.pluk.reader.ui.onboarding

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pluk.reader.domain.account.AccountRepository
import com.pluk.reader.domain.account.AuthError
import com.pluk.reader.domain.account.AuthRepository
import com.pluk.reader.domain.account.authError
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Verificar email, O3 (ONB-007, ONB-008). */
@HiltViewModel
class VerifyEmailViewModel @Inject constructor(
    private val auth: AuthRepository,
    private val accounts: AccountRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    sealed interface Next {
        data object Interests : Next

        /** Volver a crear cuenta con estos datos (ONB-008). */
        data class Change(val name: String, val email: String) : Next
    }

    data class UiState(
        val email: String = "",
        val name: String = "",
        val resendSeconds: Int = 0,
        val busy: Boolean = false,
        val error: AuthError? = null,
        val resent: Boolean = false,
        val next: Next? = null,
    ) {
        val canResend: Boolean get() = resendSeconds == 0 && !busy
    }

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var polling: Job? = null
    private var cooldown: Job? = null

    init {
        viewModelScope.launch {
            val user = accounts.user.first()
            _state.update { it.copy(email = user?.email.orEmpty(), name = user?.displayName.orEmpty()) }
        }
        // Al llegar desde crear cuenta el email acaba de salir: hay que esperar para reenviarlo.
        if (savedStateHandle.get<Boolean>(ARG_JUST_SENT) == true) startCooldown()
    }

    /** ONB-007: mientras la pantalla está a la vista, pregunta cada [POLL_MS] si ya se verificó. */
    fun onVisible() {
        if (polling?.isActive == true) return
        polling = viewModelScope.launch {
            while (true) {
                if (auth.refreshEmailVerified().getOrDefault(false)) {
                    _state.update { it.copy(next = Next.Interests) }
                    return@launch
                }
                delay(POLL_MS)
            }
        }
    }

    fun onHidden() {
        polling?.cancel()
        polling = null
    }

    fun resend() {
        if (!_state.value.canResend) return
        _state.update { it.copy(busy = true, error = null, resent = false) }
        viewModelScope.launch {
            auth.sendEmailVerification().fold(
                onSuccess = {
                    _state.update { it.copy(busy = false, resent = true) }
                    startCooldown()
                },
                onFailure = { e -> _state.update { it.copy(busy = false, error = e.authError()) } },
            )
        }
    }

    /** ONB-008: "Cambiar" y la flecha atrás. Si ya se verificó, sigue adelante en lugar de borrar la cuenta. */
    fun change() {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            if (auth.refreshEmailVerified().getOrDefault(false)) {
                _state.update { it.copy(busy = false, next = Next.Interests) }
                return@launch
            }
            auth.discardUnverifiedAccount().fold(
                onSuccess = {
                    onHidden()
                    _state.update { it.copy(busy = false, next = Next.Change(it.name, it.email)) }
                },
                onFailure = { e -> _state.update { it.copy(busy = false, error = e.authError()) } },
            )
        }
    }

    fun onNavigated() = _state.update { it.copy(next = null) }

    private fun startCooldown() {
        cooldown?.cancel()
        cooldown = viewModelScope.countdown(RESEND_SECONDS) { left -> _state.update { it.copy(resendSeconds = left) } }
    }

    companion object {
        const val ARG_JUST_SENT = "justSent"
        const val RESEND_SECONDS = 60
        const val POLL_MS = 3_000L
    }
}
