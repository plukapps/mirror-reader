package com.pluk.reader.domain.home

import com.pluk.reader.domain.model.LibraryBook
import com.pluk.reader.domain.model.ReadingStatus

enum class Greeting { Morning, Afternoon, Night }

/** HOM-001: 5–11 h mañana, 12–19 h tarde, el resto noche. */
fun greetingFor(hourOfDay: Int): Greeting = when (hourOfDay) {
    in 5..11 -> Greeting.Morning
    in 12..19 -> Greeting.Afternoon
    else -> Greeting.Night
}

/** Máximo de libros por fila de Inicio (HOM-008 a HOM-010). */
const val HOME_ROW_LIMIT = 5

/**
 * Lo que muestra Inicio.
 *
 * @param continueReading libro en lectura abierto más recientemente (HOM-002), o null (HOM-003).
 * @param reading otros libros en lectura, hasta [HOME_ROW_LIMIT] (HOM-008).
 * @param recentlyAdded últimos libros importados, de cualquier estado (HOM-009).
 * @param finished últimos libros terminados (HOM-010).
 * Los `*Count` son el total de cada sección, no solo los visibles.
 */
data class HomeContent(
    val continueReading: LibraryBook?,
    val reading: List<LibraryBook>,
    val readingCount: Int,
    val recentlyAdded: List<LibraryBook>,
    val recentlyAddedCount: Int,
    val finished: List<LibraryBook>,
    val finishedCount: Int,
    val libraryEmpty: Boolean,
) {
    companion object {
        val Empty = HomeContent(null, emptyList(), 0, emptyList(), 0, emptyList(), 0, libraryEmpty = true)
    }
}

/** [books] llega con el importado más reciente primero; los órdenes por fecha son estables en empates. */
fun homeContent(books: List<LibraryBook>): HomeContent {
    val inProgress = books.filter { it.status == ReadingStatus.Reading }.sortedByDescending { it.lastReadAt ?: 0L }
    val others = inProgress.drop(1)
    val finished = books.filter { it.status == ReadingStatus.Finished }.sortedByDescending { it.lastReadAt ?: 0L }
    return HomeContent(
        continueReading = inProgress.firstOrNull(),
        reading = others.take(HOME_ROW_LIMIT),
        readingCount = others.size,
        recentlyAdded = books.sortedByDescending { it.addedAt }.take(HOME_ROW_LIMIT),
        recentlyAddedCount = books.size,
        finished = finished.take(HOME_ROW_LIMIT),
        finishedCount = finished.size,
        libraryEmpty = books.isEmpty(),
    )
}
