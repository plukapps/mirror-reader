package com.pluk.reader.domain.model

import com.pluk.reader.domain.progressPercent

enum class ReadingStatus { New, Reading, Finished }

/** Filtro de la biblioteca por estado de lectura (LIB-010). */
enum class LibraryFilter { All, Reading, Finished }

/**
 * Libro de la biblioteca tal como lo muestra la grilla (LIB-011).
 *
 * @param id hash del contenido (LIB-003).
 * @param coverPath ruta del archivo de portada, o null si el EPUB no trae una.
 * @param progressPercent 0..100, o null si el libro nunca se abrió.
 * @param addedAt instante de importación (HOM-009).
 * @param lastReadAt instante de la última posición guardada, o null si nunca se abrió (HOM-002).
 */
data class LibraryBook(
    val id: String,
    val title: String,
    val author: String?,
    val coverPath: String?,
    val progressPercent: Int?,
    val lastReadAt: Long? = null,
    val addedAt: Long = 0L,
) {
    val status: ReadingStatus = readingStatus(progressPercent)
}

/** Sin progreso: nuevo. Al 100 %: terminado. Cualquier otro caso: leyendo (LIB-010, LIB-011). */
fun readingStatus(progressPercent: Int?): ReadingStatus = when {
    progressPercent == null -> ReadingStatus.New
    progressPercent >= 100 -> ReadingStatus.Finished
    else -> ReadingStatus.Reading
}

fun List<LibraryBook>.filterBy(filter: LibraryFilter): List<LibraryBook> = when (filter) {
    LibraryFilter.All -> this
    LibraryFilter.Reading -> filter { it.status == ReadingStatus.Reading }
    LibraryFilter.Finished -> filter { it.status == ReadingStatus.Finished }
}

/** Cantidades para las pestañas "Todos N", "Leyendo N" y "Terminados N". */
fun List<LibraryBook>.countBy(filter: LibraryFilter): Int = filterBy(filter).size

/** Convierte la progresión guardada (0.0..1.0) en el porcentaje que usa la biblioteca. */
fun libraryProgress(totalProgression: Double?): Int? = progressPercent(totalProgression)
