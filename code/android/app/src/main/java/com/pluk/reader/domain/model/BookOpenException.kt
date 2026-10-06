package com.pluk.reader.domain.model

/** El libro no se pudo abrir. El mensaje está en español y es apto para mostrarlo al usuario (LIB-002). */
class BookOpenException(message: String, cause: Throwable? = null) : Exception(message, cause)
