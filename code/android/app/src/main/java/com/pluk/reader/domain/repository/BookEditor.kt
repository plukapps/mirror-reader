package com.pluk.reader.domain.repository

/** Cambios del usuario sobre un libro de este dispositivo (LIB-019, LIB-020). */
interface BookEditor {
    /** Usa la imagen de [imageUri] como portada. Falso si no se pudo leer o guardar. */
    suspend fun replaceCover(bookId: String, imageUri: String): Boolean

    /**
     * Borra el libro de este dispositivo (registro, posición, archivo y portada) y recuerda que se quitó, para que la
     * sincronización no lo vuelva a traer.
     */
    suspend fun remove(bookId: String)
}
