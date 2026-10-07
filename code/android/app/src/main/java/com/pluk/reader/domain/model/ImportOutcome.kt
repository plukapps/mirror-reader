package com.pluk.reader.domain.model

/** Resultado de importar un EPUB a la biblioteca (LIB-001, LIB-002, LIB-003). */
sealed interface ImportOutcome {
    data class Imported(val bookId: String, val title: String) : ImportOutcome

    /** El mismo contenido ya estaba en la biblioteca: no se duplica (LIB-003). */
    data class AlreadyInLibrary(val bookId: String, val title: String) : ImportOutcome

    /** Corrupto, con DRM o ilegible (LIB-002). [message] está listo para mostrarse. */
    data class Rejected(val message: String) : ImportOutcome
}
