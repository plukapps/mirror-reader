package com.pluk.reader.ui.library

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
