package com.pluk.reader.domain.repository

/** Libros quitados de este dispositivo y su paso por la nube (LIB-020). */
interface BookRemovals {
    /** Libros quitados aquí que la nube todavía no sabe que se borraron. */
    suspend fun pendingCloudRemovals(): List<String>

    /** La nube ya los tiene borrados (marca y archivos). */
    suspend fun markCloudRemoved(bookId: String)

    /**
     * Quita de este dispositivo los libros que se borraron desde otro (LIB-008). Solo los que ya estaban en la nube:
     * uno importado aquí y aún no subido es otra copia y no se toca. Los que no están aquí se ignoran.
     */
    suspend fun removeDeletedInCloud(bookIds: List<String>)
}
