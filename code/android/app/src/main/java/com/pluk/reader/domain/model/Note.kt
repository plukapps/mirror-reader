package com.pluk.reader.domain.model

/**
 * Nota sobre un texto del libro (ANN-003). El texto queda subrayado en el lector (ANN-008).
 *
 * @param locatorJson lugar del texto en el EPUB, serializado como la posición de lectura: el dominio no lo interpreta.
 * @param quote el texto citado, para mostrarlo y reubicarlo si el lugar ya no resuelve (ANN-006).
 * @param progression progresión total 0.0..1.0 del lugar, para ordenar y mostrar el porcentaje (ANN-010).
 */
data class Note(
    val id: String,
    val bookId: String,
    val locatorJson: String,
    val quote: String,
    val text: String,
    val progression: Double,
    val createdAt: Long,
    val updatedAt: Long,
) {
    /** Porcentaje del libro donde está la nota (ANN-010). */
    val percent: Int get() = (progression.coerceIn(0.0, 1.0) * 100).toInt()
}
