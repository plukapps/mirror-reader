package com.pluk.reader.ui.actions

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.roundToInt

/**
 * Dónde va la tarjeta del menú de un libro (LIB-016, diseño 05b): centrada sobre la portada y [gap] por encima. Si no
 * entra arriba, va debajo; si tampoco, queda dentro de la pantalla. Siempre a [margin] de los bordes y fuera de las
 * barras del sistema ([safeTop], [safeBottom]). Todo en píxeles.
 */
fun placeMenu(
    anchor: Rect,
    menu: IntSize,
    container: IntSize,
    gap: Int,
    margin: Int,
    safeTop: Int,
    safeBottom: Int,
): IntOffset {
    val minX = margin
    val maxX = (container.width - margin - menu.width).coerceAtLeast(minX)
    val x = (anchor.center.x - menu.width / 2f).roundToInt().coerceIn(minX, maxX)

    val minY = safeTop + margin
    val maxY = (container.height - safeBottom - margin - menu.height).coerceAtLeast(minY)
    val above = (anchor.top - gap - menu.height).roundToInt()
    val below = (anchor.bottom + gap).roundToInt()
    val y = when {
        above >= minY -> above
        below <= maxY -> below
        else -> maxY
    }
    return IntOffset(x, y)
}
