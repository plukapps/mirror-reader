package com.pluk.reader.domain

/**
 * Texto del comienzo de la página, para volver a encontrarla en otro dispositivo aunque pagine distinto (SYN-014).
 * Va en el locator como `text`: Readium busca [highlight] en el capítulo, con [before] y [after] como contexto.
 */
data class TextAnchor(val before: String?, val highlight: String, val after: String?)

/** Caracteres de cada parte del ancla. */
const val TEXT_ANCHOR_LENGTH = 32

/**
 * Arma el ancla desde [start], el primer carácter visible de la página dentro de [window] (un tramo del texto del
 * capítulo). Salta los espacios del principio; sin texto visible devuelve null. Los índices son UTF-16, como los de
 * JavaScript, y ningún corte parte un par sustituto. Función pura.
 */
fun textAnchorAt(window: String, start: Int): TextAnchor? {
    if (start !in window.indices) return null
    val from = (start until window.length).firstOrNull { !window[it].isWhitespace() } ?: return null
    val to = window.cut(from + TEXT_ANCHOR_LENGTH)
    val afterEnd = window.cut(to + TEXT_ANCHOR_LENGTH)
    val beforeStart = window.cut(from - TEXT_ANCHOR_LENGTH)
    return TextAnchor(
        before = window.substring(beforeStart, from).ifEmpty { null },
        highlight = window.substring(from, to),
        after = window.substring(to, afterEnd).ifEmpty { null },
    )
}

/** [index] dentro del texto, corrido un carácter hacia adelante si cae en la mitad de un par sustituto. */
private fun String.cut(index: Int): Int {
    val i = index.coerceIn(0, length)
    return if (i in 1 until length && this[i].isLowSurrogate()) i + 1 else i
}
