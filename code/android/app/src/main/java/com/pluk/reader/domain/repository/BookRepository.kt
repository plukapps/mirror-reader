package com.pluk.reader.domain.repository

import com.pluk.reader.domain.model.OpenedBook

interface BookRepository {
    /** Abre el EPUB en [uri] y recupera su posición guardada. El fallo es una `BookOpenException`. */
    suspend fun open(uri: String): Result<OpenedBook>
}
