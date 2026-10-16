package com.pluk.reader.domain.help

/** Preguntas frecuentes (PRF-017, PRF-018). Viven en la app: se leen sin conexión. Los textos están en la UI. */
enum class FaqId(val key: String) {
    EPUB("epub"),
    BOOKS("books"),
    SPACE("space"),
    ADD("add"),
    SYNC("sync"),
    REMOVE("remove"),
}

object Faq {
    /** Las tres de la tarjeta de ayuda de Perfil, en el orden del diseño. */
    val card: List<FaqId> = listOf(FaqId.EPUB, FaqId.BOOKS, FaqId.SPACE)

    /** "Todas las preguntas": primero las de la tarjeta, después el resto. */
    val all: List<FaqId> = card + FaqId.entries.filterNot { it in card }

    fun byKey(key: String): FaqId? = FaqId.entries.firstOrNull { it.key == key }
}
