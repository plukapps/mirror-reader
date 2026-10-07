package com.pluk.reader.domain.repository

import com.pluk.reader.domain.model.OpenedBook

interface BookRepository {
    /** Abre el libro [bookId] de la biblioteca y recupera su posición guardada. El fallo es una `BookOpenException`. */
    suspend fun open(bookId: String): Result<OpenedBook>
}
