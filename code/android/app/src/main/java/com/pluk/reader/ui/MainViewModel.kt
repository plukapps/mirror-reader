package com.pluk.reader.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pluk.reader.domain.account.AccountRepository
import com.pluk.reader.domain.account.AuthRepository
import com.pluk.reader.domain.model.ImportOutcome
import com.pluk.reader.domain.repository.LibraryRepository
import com.pluk.reader.domain.onboarding.StartDestination
import com.pluk.reader.domain.onboarding.startDestination
import com.pluk.reader.domain.usecase.LibrarySync
import com.pluk.reader.domain.usecase.PositionSync
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Decide la pantalla de arranque (ONB-019) y recibe EPUB de "Abrir con" (AND-003): los importa a la
 * biblioteca y devuelve cómo abrirlos.
 */
@HiltViewModel
class MainViewModel @Inject constructor(
    private val library: LibraryRepository,
    private val librarySync: LibrarySync,
    private val positionSync: PositionSync,
    private val accounts: AccountRepository,
    private val auth: AuthRepository,
) : ViewModel() {

    enum class Start { Loading, Welcome, VerifyEmail, Home }

    /**
     * ONB-019, WEL-006: se decide una vez por arranque, con la sesión que el dispositivo ya conoce (sin red).
     * Si la sesión cambia con la app abierta, no se reubica al usuario: el onboarding navega por su cuenta.
     */
    val start: StateFlow<Start> = flow {
        emit(
            when (startDestination(accounts.user.first())) {
                StartDestination.Welcome -> Start.Welcome
                StartDestination.VerifyEmail -> Start.VerifyEmail
                StartDestination.Home -> Start.Home
            },
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, Start.Loading)

    /**
     * SYN-001, LIB-007: con sesión, la biblioteca se sincroniza sola al abrir la app (y al iniciar sesión).
     * La actividad lo llama al crearse (el ViewModel se crea recién al usarlo, no basta con un `init`).
     * Llamarlo de nuevo no duplica el trabajo.
     */
    fun startLibrarySync() = librarySync.start()

    /**
     * SYN-001, SYN-011, SYN-012: la app volvió a primer plano. Sincroniza la biblioteca si la última pasada exitosa
     * es vieja (TTL), reenvía las posiciones pendientes y empieza a recibir las de otros dispositivos.
     */
    fun onAppStarted() {
        librarySync.requestIfStale()
        positionSync.requestFlush()
        positionSync.startListening()
    }

    /** SYN-011, SYN-012: la app dejó de verse. Envía la posición de lectura y deja de escuchar (sin trabajo en segundo plano). */
    fun onAppStopped() {
        positionSync.stopListening()
        positionSync.requestFlush()
    }

    /**
     * ONB-007: la app abrió el enlace de verificación del email. Avisa con [onResult] si salió bien; la pantalla
     * de verificación, si está a la vista, avanza sola.
     */
    fun verifyEmailLink(code: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch { onResult(auth.applyEmailVerification(code).isSuccess) }
    }

    sealed interface Incoming {
        data class Open(val bookId: String) : Incoming
        data class Failed(val message: String) : Incoming
    }

    suspend fun importIncoming(uri: String): Incoming = when (val outcome = library.import(uri)) {
        is ImportOutcome.Imported -> {
            librarySync.request()
            Incoming.Open(outcome.bookId)
        }
        is ImportOutcome.AlreadyInLibrary -> Incoming.Open(outcome.bookId)
        is ImportOutcome.Rejected -> Incoming.Failed(outcome.message)
    }
}
