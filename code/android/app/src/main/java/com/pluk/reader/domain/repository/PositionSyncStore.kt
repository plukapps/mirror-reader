package com.pluk.reader.domain.repository

import com.pluk.reader.domain.model.LocalPosition
import com.pluk.reader.domain.model.ReadingPosition

/** Lado local de la sincronización de la posición de lectura (SYN-011, SYN-012). */
interface PositionSyncStore {
    /** La posición guardada del libro y si la nube ya la tiene, o null si nunca se leyó. */
    suspend fun local(bookId: String): LocalPosition?

    /** Posiciones que la nube aún no tiene y cuyo libro ya está en la nube (el servidor exige que el libro exista). */
    suspend fun pending(): List<ReadingPosition>

    /** Marca como enviada la lectura de [readAt]. Si mientras tanto se leyó más, sigue pendiente. */
    suspend fun markSynced(bookId: String, readAt: Long)

    /** Vuelve a marcar la posición del libro como pendiente de enviar. */
    suspend fun markPending(bookId: String)

    /** Guarda la posición que vino de la nube como ya enviada, solo si es más reciente que la local. */
    suspend fun applyRemote(position: ReadingPosition)
}
