package com.pluk.reader.domain

private const val MIN_WIDTH_DP = 840
private const val MIN_HEIGHT_DP = 480

/** RDR-016, AND-006: dos páginas si la ventana es ancha, no es baja y es más ancha que alta. */
fun useTwoPages(widthDp: Int, heightDp: Int): Boolean =
    widthDp >= MIN_WIDTH_DP && heightDp >= MIN_HEIGHT_DP && widthDp > heightDp

/** RDR-010, RDR-016: texto del pie. Con dos páginas muestra el par ("107–108"); en la última posición, solo una. */
fun pageLabel(position: Int?, twoPages: Boolean, lastPosition: Int?): String? = when {
    position == null -> null
    !twoPages || (lastPosition != null && position >= lastPosition) -> position.toString()
    else -> "$position–${position + 1}"
}
