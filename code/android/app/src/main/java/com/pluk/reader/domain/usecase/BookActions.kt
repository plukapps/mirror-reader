package com.pluk.reader.domain.usecase

import com.pluk.reader.domain.model.UNREAD_LOCATOR
import com.pluk.reader.domain.repository.BookEditor
import com.pluk.reader.domain.repository.PositionRepository
import javax.inject.Inject

/** Pide enviar las posiciones pendientes sin esperar el resultado (SYN-011). */
fun interface PositionFlushRequester {
    fun requestFlush()
}

/** Acciones del menú de un libro (LIB-016 a LIB-020). "Leer libro" es navegación y no pasa por aquí. */
class BookActions @Inject constructor(
    private val positions: PositionRepository,
    private val editor: BookEditor,
    private val flusher: PositionFlushRequester,
    private val removals: RemovalPushRequester,
) {
    /** Deja el libro al 100 % conservando su última posición (LIB-018). */
    suspend fun markFinished(bookId: String) {
        positions.save(bookId, positions.get(bookId) ?: UNREAD_LOCATOR, 1.0)
        flusher.requestFlush()
    }

    /** El libro vuelve a estar sin empezar y se abre desde el principio (LIB-018). */
    suspend fun markUnread(bookId: String) {
        positions.save(bookId, UNREAD_LOCATOR, 0.0)
        flusher.requestFlush()
    }

    suspend fun setCover(bookId: String, imageUri: String): Boolean = editor.replaceCover(bookId, imageUri)

    /** Lo quita de aquí al instante y lo borra de la cuenta en cuanto haya conexión (LIB-020). */
    suspend fun remove(bookId: String) {
        editor.remove(bookId)
        removals.requestPush()
    }
}
