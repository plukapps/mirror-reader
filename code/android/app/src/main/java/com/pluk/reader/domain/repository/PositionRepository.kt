package com.pluk.reader.domain.repository

/** Posición de lectura por libro (RDR-006). El locator viaja serializado: el dominio no lo interpreta. */
interface PositionRepository {
    suspend fun get(bookId: String): String?
    suspend fun save(bookId: String, locatorJson: String, totalProgression: Double?)
}
