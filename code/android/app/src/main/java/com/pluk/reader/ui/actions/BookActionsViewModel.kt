package com.pluk.reader.ui.actions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pluk.reader.domain.model.LibraryBook
import com.pluk.reader.domain.model.ReadingStatus
import com.pluk.reader.domain.usecase.BookActions
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

/** Opciones del menú de un libro, en el orden del diseño 05b (LIB-016). */
enum class BookAction { Read, MarkFinished, MarkUnread, ChangeCover, Remove }

/** En un libro terminado, "Marcar como no leído" reemplaza a "Marcar como terminado" (LIB-018). */
fun menuActions(book: LibraryBook): List<BookAction> = listOf(
    BookAction.Read,
    if (book.status == ReadingStatus.Finished) BookAction.MarkUnread else BookAction.MarkFinished,
    BookAction.ChangeCover,
    BookAction.Remove,
)

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
) : ViewModel() {
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
