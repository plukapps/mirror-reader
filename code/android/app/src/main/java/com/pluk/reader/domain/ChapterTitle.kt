package com.pluk.reader.domain

/** Entrada de la tabla de contenidos, a cualquier nivel, en el orden en que aparece: título y recurso (puede traer fragmento). */
data class ChapterEntry(val title: String, val href: String)

/**
 * Título del capítulo donde está el lector (RDR-013), o vacío si no se puede deducir.
 *
 * [toc] es la tabla de contenidos aplanada y [readingOrder] la lista de recursos del libro. El capítulo es la
 * última entrada cuyo recurso no viene después del actual: un recurso sin entrada propia (continuación) sigue
 * en el capítulo anterior. Si varias entradas apuntan al mismo recurso gana la de fragmento exacto, y si no la primera.
 */
fun currentChapterTitle(toc: List<ChapterEntry>, readingOrder: List<String>, href: String): String {
    val current = readingOrder.indexOf(href.substringBefore('#'))
    if (current < 0) return ""
    val located = toc.mapNotNull { entry ->
        readingOrder.indexOf(entry.href.substringBefore('#')).takeIf { it >= 0 }?.let { it to entry }
    }
    val bestIndex = located.map { it.first }.filter { it <= current }.maxOrNull() ?: return ""
    val sameResource = located.filter { it.first == bestIndex }.map { it.second }
    val fragment = href.substringAfter('#', "")
    val exact = if (bestIndex == current && fragment.isNotEmpty()) sameResource.firstOrNull { it.href == href } else null
    return (exact ?: sameResource.first()).title
}
