package com.pluk.reader.domain.model

/**
 * Posición de lectura de un libro (RDR-006). El locator viaja serializado: el dominio no lo interpreta.
 *
 * @param progress progresión total de 0.0 a 1.0, o null si se desconoce.
 * @param readAt cuándo se leyó, según el reloj del dispositivo, en milisegundos. Decide cuál gana (SYN-003).
 */
data class ReadingPosition(
    val bookId: String,
    val locatorJson: String,
    val progress: Double?,
    val readAt: Long,
)

/** La posición guardada en este dispositivo. [isSynced]: la nube ya tiene esta lectura. */
data class LocalPosition(val position: ReadingPosition, val isSynced: Boolean)

/** La posición que hay en la nube, con el dispositivo que la escribió. */
data class RemotePosition(val position: ReadingPosition, val deviceId: String, val deviceName: String)
