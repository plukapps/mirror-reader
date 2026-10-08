package com.pluk.reader.ui

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pluk.reader.domain.account.AccountRepository
import com.pluk.reader.domain.model.ImportOutcome
import com.pluk.reader.domain.repository.LibraryRepository
import com.pluk.reader.domain.usecase.SyncCoversUseCase
import com.pluk.reader.domain.usecase.SyncRemoteBooksUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

/** Recibe EPUB de "Abrir con" (AND-003): los importa a la biblioteca y devuelve cómo abrirlos. */
@HiltViewModel
class MainViewModel @Inject constructor(
    private val library: LibraryRepository,
    private val account: AccountRepository,
    private val syncRemoteBooks: SyncRemoteBooksUseCase,
    private val syncCovers: SyncCoversUseCase,
) : ViewModel() {
    private var syncJob: Job? = null

    /**
     * LIB-007: con sesión, los libros de la nube aparecen en la biblioteca. Sin conexión no pasa nada.
     * La actividad lo llama al crearse (el ViewModel se crea recién al usarlo, no basta con un `init`).
     * Llamarlo de nuevo no duplica el trabajo.
     */
    fun syncCloudBooks() {
        if (syncJob?.isActive == true) return
        syncJob = viewModelScope.launch {
            account.user.filterNotNull().distinctUntilChangedBy { it.id }.collect {
                // Primero los libros; las portadas (LIB-012) van después y no frenan nada.
                syncRemoteBooks()
                syncCovers()
            }
        }
    }

    sealed interface Incoming {
        data class Open(val bookId: String) : Incoming
        data class Failed(val message: String) : Incoming
    }

    suspend fun importIncoming(uri: Uri): Incoming = when (val outcome = library.import(uri.toString())) {
        is ImportOutcome.Imported -> Incoming.Open(outcome.bookId)
        is ImportOutcome.AlreadyInLibrary -> Incoming.Open(outcome.bookId)
        is ImportOutcome.Rejected -> Incoming.Failed(outcome.message)
    }
}
