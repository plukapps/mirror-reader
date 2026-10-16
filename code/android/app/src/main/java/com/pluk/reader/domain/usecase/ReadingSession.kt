package com.pluk.reader.domain.usecase

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Qué libros están abiertos en el lector. Un libro abierto no se quita hasta cerrarlo (LIB-020). */
@Singleton
class ReadingSession @Inject constructor() {
    private val _open = MutableStateFlow<Set<String>>(emptySet())
    val open: StateFlow<Set<String>> = _open.asStateFlow()

    fun opened(bookId: String) = _open.update { it + bookId }

    fun closed(bookId: String) = _open.update { it - bookId }
}
