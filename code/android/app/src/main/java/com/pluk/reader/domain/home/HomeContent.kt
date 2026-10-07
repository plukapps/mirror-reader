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

/**
 * Lo que muestra Inicio.
 *
 * @param continueReading libro en lectura abierto más recientemente (HOM-002), o null (HOM-003).
 * @param forYou libros nuevos de la biblioteca, en el orden de ésta (HOM-004).
 */
data class HomeContent(
    val continueReading: LibraryBook?,
    val forYou: List<LibraryBook>,
    val libraryEmpty: Boolean,
)

/** [books] llega con el importado más reciente primero; `maxByOrNull` conserva ese orden en empates. */
fun homeContent(books: List<LibraryBook>): HomeContent = HomeContent(
    continueReading = books
        .filter { it.status == ReadingStatus.Reading }
        .maxByOrNull { it.lastReadAt ?: 0L },
    forYou = books.filter { it.status == ReadingStatus.New },
    libraryEmpty = books.isEmpty(),
)
