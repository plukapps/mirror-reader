package com.pluk.reader.domain.model

import org.readium.r2.shared.publication.Publication

/**
 * Libro abierto y listo para leer.
 *
 * Es el único modelo de dominio que usa un tipo de Readium (ADR 0005): cada plataforma es nativa,
 * así que el dominio no se comparte y no vale la pena envolver `Publication`.
 *
 * @param bookId identificador estable del libro, clave de la posición guardada.
 * @param resumeLocatorJson última posición guardada, serializada, o null si no hay.
 */
data class OpenedBook(
    val publication: Publication,
    val bookId: String,
    val resumeLocatorJson: String?,
)
