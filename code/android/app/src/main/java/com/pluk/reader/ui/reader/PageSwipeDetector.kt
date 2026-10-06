package com.pluk.reader.ui.reader

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/**
 * Detecta un deslizar horizontal sobre el lector y avisa al soltar (RDR-009).
 *
 * Observa en el pase `Initial`, antes que el WebView, y solo consume los eventos cuando el gesto ya es
 * claramente horizontal. Así los toques, la pulsación larga (selección de texto) y el scroll vertical
 * siguen llegando al libro sin cambios.
 */
suspend fun PointerInputScope.detectPageSwipes(onSwipe: (PageTurnDirection) -> Unit) {
    val slop = viewConfiguration.touchSlop
    val threshold = SWIPE_THRESHOLD.toPx()
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        var total = Offset.Zero
        var swiping = false
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val change = event.changes.firstOrNull { it.id == down.id } ?: return@awaitEachGesture
            if (!change.pressed) {
                if (swiping && abs(total.x) >= threshold) {
                    onSwipe(if (total.x < 0) PageTurnDirection.Forward else PageTurnDirection.Backward)
                }
                return@awaitEachGesture
            }
            total += change.positionChange()
            if (!swiping && abs(total.x) > slop && abs(total.x) > HORIZONTAL_BIAS * abs(total.y)) swiping = true
            if (swiping) change.consume()
        }
    }
}

private val SWIPE_THRESHOLD = 48.dp
private const val HORIZONTAL_BIAS = 2f
