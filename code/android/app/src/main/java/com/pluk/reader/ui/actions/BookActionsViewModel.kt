package com.pluk.reader.ui.actions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pluk.reader.domain.account.AccountRepository
import com.pluk.reader.domain.model.LibraryBook
import com.pluk.reader.domain.model.ReadingStatus
import com.pluk.reader.domain.usecase.BookActions
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

/** Opciones del menú de un libro, en el orden del diseño 05b (LIB-016), más "Ver detalle" (LIB-022). */
enum class BookAction { Read, Details, MarkFinished, MarkUnread, ChangeCover, Remove }

/** Sin empezar: terminado. Leyendo: terminado y no leído. Terminado: no leído (LIB-018). */
fun menuActions(book: LibraryBook): List<BookAction> = buildList {
    add(BookAction.Read)
    add(BookAction.Details)
    if (book.status != ReadingStatus.Finished) add(BookAction.MarkFinished)
    if (book.status != ReadingStatus.New) add(BookAction.MarkUnread)
    add(BookAction.ChangeCover)
    add(BookAction.Remove)
}

sealed interface BookActionMessage {
    data object MarkedFinished : BookActionMessage
    data object MarkedUnread : BookActionMessage
    data object CoverFailed : BookActionMessage
    data class Removed(val title: String) : BookActionMessage
}

/** Ejecuta las acciones del menú de un libro (LIB-016 a LIB-020). Las pantallas ven el cambio en la biblioteca. */
@HiltViewModel
class BookActionsViewModel @Inject constructor(
    private val actions: BookActions,
    account: AccountRepository,
) : ViewModel() {
    /** GST-008: sin sesión, la confirmación de quitar habla solo de este dispositivo. */
    val signedIn: StateFlow<Boolean> = account.user.map { it != null }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val _messages = MutableSharedFlow<BookActionMessage>(extraBufferCapacity = MESSAGE_BUFFER)
    val messages: SharedFlow<BookActionMessage> = _messages.asSharedFlow()

    fun markFinished(book: LibraryBook) = run(BookActionMessage.MarkedFinished) { actions.markFinished(book.id) }

    fun markUnread(book: LibraryBook) = run(BookActionMessage.MarkedUnread) { actions.markUnread(book.id) }

    fun changeCover(bookId: String, imageUri: String) {
        viewModelScope.launch {
            if (!actions.setCover(bookId, imageUri)) _messages.tryEmit(BookActionMessage.CoverFailed)
        }
    }

    fun remove(book: LibraryBook) = run(BookActionMessage.Removed(book.title)) { actions.remove(book.id) }

    private fun run(message: BookActionMessage, action: suspend () -> Unit) {
        viewModelScope.launch {
            action()
            _messages.tryEmit(message)
        }
    }

    private companion object {
        const val MESSAGE_BUFFER = 4
    }
}
