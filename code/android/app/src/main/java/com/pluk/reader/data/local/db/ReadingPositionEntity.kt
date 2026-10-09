package com.pluk.reader.data.local.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Última posición de lectura de un libro (RDR-006). [updatedAt] es el momento de la lectura según el reloj del
 * dispositivo (`readAt` en la nube, SYN-003).
 *
 * @param isSynced la nube ya tiene esta lectura (SYN-011). Falso: pendiente de enviar. Las posiciones anteriores
 * a la versión 5 quedan pendientes y se envían una vez.
 */
@Entity(tableName = "reading_positions")
data class ReadingPositionEntity(
    @PrimaryKey val bookId: String,
    val locatorJson: String,
    val updatedAt: Long,
    /** Progresión total 0.0..1.0 al guardar, para la biblioteca (LIB-011). Null si se desconoce. */
    val progress: Double? = null,
    @ColumnInfo(defaultValue = "0") val isSynced: Boolean = false,
)
