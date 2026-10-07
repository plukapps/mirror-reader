package com.pluk.reader.ui.library

import android.content.Context
import com.pluk.reader.R
import com.pluk.reader.domain.model.ImportOutcome
import com.pluk.reader.domain.model.LibraryBook
import com.pluk.reader.domain.model.LibraryFilter

data class LibraryUiState(
    val loading: Boolean = true,
    /** Libros que pasan el filtro activo (LIB-010). */
    val books: List<LibraryBook> = emptyList(),
    val filter: LibraryFilter = LibraryFilter.All,
    val allCount: Int = 0,
    val readingCount: Int = 0,
    val finishedCount: Int = 0,
    val importing: Boolean = false,
) {
    /** No hay ningún libro, sin importar el filtro. */
    val isLibraryEmpty: Boolean get() = !loading && allCount == 0
}

/** Aviso al usuario tras importar (LIB-001, LIB-002, LIB-003). La pantalla lo traduce a texto. */
sealed interface LibraryMessage {
    data class Imported(val count: Int) : LibraryMessage
    data class AlreadyInLibrary(val title: String) : LibraryMessage
    data class Rejected(val reason: String) : LibraryMessage
}

/** Avisos de un lote de importación: primero el total importado, luego cada duplicado o rechazo. */
fun List<ImportOutcome>.toMessages(): List<LibraryMessage> {
    val imported = count { it is ImportOutcome.Imported }
    return listOfNotNull(imported.takeIf { it > 0 }?.let { LibraryMessage.Imported(it) }) + mapNotNull {
        when (it) {
            is ImportOutcome.AlreadyInLibrary -> LibraryMessage.AlreadyInLibrary(it.title)
            is ImportOutcome.Rejected -> LibraryMessage.Rejected(it.message)
            is ImportOutcome.Imported -> null
        }
    }
}

/** Texto del aviso para mostrar en un snackbar. */
fun LibraryMessage.toText(context: Context): String = when (this) {
    is LibraryMessage.Imported -> context.resources.getQuantityString(R.plurals.library_imported, count, count)
    is LibraryMessage.AlreadyInLibrary -> context.getString(R.string.library_already_in, title)
    is LibraryMessage.Rejected -> reason
}
