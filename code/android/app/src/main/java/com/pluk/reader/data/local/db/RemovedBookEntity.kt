package com.pluk.reader.data.local.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Libro que el usuario quitó de este dispositivo (LIB-020). La sincronización no lo vuelve a agregar; importar el
 * mismo archivo borra la marca. Más adelante sirve para propagar el borrado a la nube (K-154).
 */
@Entity(tableName = "removed_books")
data class RemovedBookEntity(
    @PrimaryKey val id: String,
    val removedAt: Long,
)
