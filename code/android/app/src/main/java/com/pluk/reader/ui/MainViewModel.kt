package com.pluk.reader.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pluk.reader.domain.account.AccountRepository
import com.pluk.reader.domain.model.ImportOutcome
import com.pluk.reader.domain.repository.LibraryRepository
import com.pluk.reader.domain.repository.WelcomeRepository
import com.pluk.reader.domain.showsWelcome
import com.pluk.reader.domain.usecase.LibrarySync
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Decide la pantalla de arranque (WEL-003) y recibe EPUB de "Abrir con" (AND-003): los importa a la
 * biblioteca y devuelve cómo abrirlos.
 */
@HiltViewModel
class MainViewModel @Inject constructor(
    private val library: LibraryRepository,
    private val librarySync: LibrarySync,
    private val welcome: WelcomeRepository,
    private val accounts: AccountRepository,
) : ViewModel() {

    enum class Start { Loading, Welcome, Home }

    /**
     * WEL-003, WEL-006: se decide una vez por arranque, con la marca guardada y la sesión que el
     * dispositivo ya conoce (sin red). Si la sesión cambia con la app abierta, no se reubica al usuario.
     */
    val start: StateFlow<Start> = flow {
        val completed = welcome.completed.first()
        val signedIn = accounts.user.first() != null
        emit(if (showsWelcome(completed, signedIn)) Start.Welcome else Start.Home)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, Start.Loading)

    /** WEL-005: "Comenzar". La navegación a Inicio no espera a que se guarde. */
    fun completeWelcome() {
        viewModelScope.launch { welcome.markCompleted() }
    }

    /**
     * SYN-001, LIB-007: con sesión, la biblioteca se sincroniza sola al abrir la app (y al iniciar sesión).
     * La actividad lo llama al crearse (el ViewModel se crea recién al usarlo, no basta con un `init`).
     * Llamarlo de nuevo no duplica el trabajo.
     */
    fun startLibrarySync() = librarySync.start()

    /** SYN-001: la app volvió a primer plano. Sincroniza si la última pasada exitosa es vieja (TTL). */
    fun onAppStarted() = librarySync.requestIfStale()

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
