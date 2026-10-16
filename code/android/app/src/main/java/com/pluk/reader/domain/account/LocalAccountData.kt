package com.pluk.reader.domain.account

/** Lo que la cuenta guarda en este dispositivo: libros, portadas, progreso y preferencias de la cuenta (PRF-009). */
interface LocalAccountData {
    /** Lo borra todo, salvo los ajustes del lector y la identidad del dispositivo. Puede fallar. */
    suspend fun wipe()
}
