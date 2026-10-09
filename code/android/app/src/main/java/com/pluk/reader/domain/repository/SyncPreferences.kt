package com.pluk.reader.domain.repository

/** Datos de este dispositivo para sincronizar la posición de lectura (SYN-011, SYN-012). */
interface SyncPreferences {
    /** Identificador de esta instalación. Se genera la primera vez y no cambia. */
    suspend fun deviceId(): String

    /** Nombre legible del dispositivo, para avisar desde dónde se leyó ("SM-X510"). */
    fun deviceName(): String

    /** Mayor `updatedAt` del servidor que ya se vio, en milisegundos, o null si nunca se sincronizó. */
    suspend fun lastPositionsSeenAt(): Long?

    suspend fun setLastPositionsSeenAt(value: Long)
}
