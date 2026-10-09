package com.pluk.reader.ui

import androidx.lifecycle.ViewModel
import com.pluk.reader.domain.model.ImportOutcome
import com.pluk.reader.domain.repository.LibraryRepository
import com.pluk.reader.domain.usecase.LibrarySync
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/** Recibe EPUB de "Abrir con" (AND-003): los importa a la biblioteca y devuelve cómo abrirlos. */
@HiltViewModel
class MainViewModel @Inject constructor(
    private val library: LibraryRepository,
    private val librarySync: LibrarySync,
) : ViewModel() {
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
