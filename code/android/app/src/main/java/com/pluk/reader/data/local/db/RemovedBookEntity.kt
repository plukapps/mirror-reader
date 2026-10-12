package com.pluk.reader.data.local.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Libro quitado de la biblioteca (LIB-020). La sincronización no lo vuelve a agregar; importar el mismo archivo
 * borra la marca.
 *
 * @param cloudDeleted la nube ya lo tiene borrado (marca y archivos), o nunca estuvo allí. Falso: falta enviarlo.
 */
@Entity(tableName = "removed_books")
data class RemovedBookEntity(
    @PrimaryKey val id: String,
    val removedAt: Long,
    @ColumnInfo(defaultValue = "0") val cloudDeleted: Boolean = false,
)
