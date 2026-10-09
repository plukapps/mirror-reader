package com.pluk.reader.data.local.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Libro de la biblioteca. [id] es el SHA-256 del contenido (LIB-003, ADR 0006).
 *
 * @param sizeBytes tamaño del archivo. 0 en libros importados antes de la versión 3 (se mide del archivo al subir).
 * @param isDownloaded el archivo está en este dispositivo. Falso: libro solo en la nube (LIB-007).
 * @param uploadedAt instante en que se subió o se vio en la nube, o null si aún no está subido.
 * @param isCoverUploaded la portada ya está en la nube (LIB-012). Evita volver a consultarla en cada sincronización.
 */
@Entity(tableName = "books")
data class BookEntity(
    @PrimaryKey val id: String,
    val title: String,
    val author: String?,
    val hasCover: Boolean,
    val addedAt: Long,
    @ColumnInfo(defaultValue = "0") val sizeBytes: Long = 0,
    @ColumnInfo(defaultValue = "1") val isDownloaded: Boolean = true,
    val uploadedAt: Long? = null,
    @ColumnInfo(defaultValue = "0") val isCoverUploaded: Boolean = false,
)
