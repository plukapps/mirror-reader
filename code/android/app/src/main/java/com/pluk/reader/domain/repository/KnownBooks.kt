package com.pluk.reader.domain.repository

/** Ids de los libros que ya están en la biblioteca de este dispositivo (descargados o solo en la nube). */
fun interface KnownBooks {
    suspend fun knownBookIds(): Set<String>
}
