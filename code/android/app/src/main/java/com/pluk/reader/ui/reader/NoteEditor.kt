package com.pluk.reader.ui.reader

/**
 * Editor de nota abierto en el lector (ANN-008, ANN-009). [noteId] es null para una nota nueva sobre la selección.
 */
data class NoteEditor(
    val noteId: String?,
    val quote: String,
    val text: String,
    val locatorJson: String,
    val progression: Double,
)
