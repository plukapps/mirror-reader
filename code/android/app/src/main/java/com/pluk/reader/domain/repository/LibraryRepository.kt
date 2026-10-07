package com.pluk.reader.domain.repository

import com.pluk.reader.domain.model.ImportOutcome
import com.pluk.reader.domain.model.LibraryBook
import kotlinx.coroutines.flow.Flow

interface LibraryRepository {
    /** Libros de la biblioteca, el último importado primero, con su progreso (LIB-011). */
    val books: Flow<List<LibraryBook>>

    /** Importa el EPUB de [uri] (LIB-001, LIB-002, LIB-003). Nunca lanza: el fallo es `Rejected`. */
    suspend fun import(uri: String): ImportOutcome
}
