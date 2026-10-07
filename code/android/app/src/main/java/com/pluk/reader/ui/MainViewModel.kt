package com.pluk.reader.ui

import android.net.Uri
import androidx.lifecycle.ViewModel
import com.pluk.reader.domain.model.ImportOutcome
import com.pluk.reader.domain.repository.LibraryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/** Recibe EPUB de "Abrir con" (AND-003): los importa a la biblioteca y devuelve cómo abrirlos. */
@HiltViewModel
class MainViewModel @Inject constructor(private val library: LibraryRepository) : ViewModel() {
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
