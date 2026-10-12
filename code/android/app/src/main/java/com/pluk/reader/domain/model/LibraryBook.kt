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
 * @param isDownloaded el archivo está en este dispositivo; si no, el libro está solo en la nube (LIB-007).
 * @param isUploaded el libro ya está en la nube.
 * @param coverVersion cambia cuando el usuario reemplaza la portada (LIB-019): la ruta es la misma.
 */
data class LibraryBook(
    val id: String,
    val title: String,
    val author: String?,
    val coverPath: String?,
    val progressPercent: Int?,
    val lastReadAt: Long? = null,
    val addedAt: Long = 0L,
    val isDownloaded: Boolean = true,
    val isUploaded: Boolean = false,
    val coverVersion: Long = 0L,
) {
    val status: ReadingStatus = readingStatus(progressPercent)
}

/** Sin progreso: nuevo. Al 100 %: terminado. Cualquier otro caso: leyendo (LIB-010, LIB-011). */
fun readingStatus(progressPercent: Int?): ReadingStatus = when {
    progressPercent == null -> ReadingStatus.New
    progressPercent >= 100 -> ReadingStatus.Finished
    else -> ReadingStatus.Reading
}

/**
 * Localizador de un libro marcado como no leído (LIB-018): viaja como una posición más, así llega a los otros
 * dispositivos, y el lector lo abre desde el principio porque no apunta a ningún capítulo (no tiene `href`). No es
 * `"{}"`, que ya se usa como posición de relleno.
 */
const val UNREAD_LOCATOR = """{"unread":true}"""

/**
 * Progresión de un libro que tiene posición guardada. Una posición "no leído" cuenta como sin abrir (null), salvo que
 * se haya marcado terminado sin abrirlo. Una posición sin progresión (libro leído antes de LIB-011) cuenta como 0 %.
 */
fun positionProgression(locatorJson: String, progress: Double?): Double? = when {
    locatorJson == UNREAD_LOCATOR && (progress ?: 0.0) < 1.0 -> null
    else -> progress ?: 0.0
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
