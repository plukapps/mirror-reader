package com.pluk.reader.domain.remote

/**
 * Fechas de entrada a la biblioteca que salen de un listado de la nube (LIB-021). [inserts] tiene la fecha de cada
 * libro que todavía no está en este dispositivo; [updates], la de los que ya están con otra fecha.
 */
data class CloudAddedAt(val inserts: Map<String, Long>, val updates: Map<String, Long>)

/**
 * Un libro entra a la biblioteca cuando entra a la cuenta: manda el `createdAt` de la nube, también sobre la hora de
 * importación local o la que se le puso al verlo. Sin `createdAt`, un libro conocido conserva su fecha y uno nuevo
 * toma [now], menos su posición en la lista para que el primero quede como el más reciente.
 *
 * @param known `addedAt` de los libros que ya están en este dispositivo, por id.
 */
fun cloudAddedAt(cloud: List<RemoteBook>, known: Map<String, Long>, now: Long): CloudAddedAt {
    val inserts = mutableMapOf<String, Long>()
    val updates = mutableMapOf<String, Long>()
    cloud.forEachIndexed { index, book ->
        val current = known[book.id]
        when {
            current == null -> inserts[book.id] = book.createdAt ?: (now - index)
            book.createdAt != null && book.createdAt != current -> updates[book.id] = book.createdAt
        }
    }
    return CloudAddedAt(inserts, updates)
}
