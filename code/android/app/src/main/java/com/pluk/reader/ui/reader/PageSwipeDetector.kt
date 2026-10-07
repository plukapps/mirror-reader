package com.pluk.reader.ui.reader

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import kotlin.math.abs

/**
 * Detecta el arrastre horizontal sobre el lector y lo cuenta como una sesión de paso de página (RDR-009).
 *
 * Observa en el pase `Initial`, antes que el WebView, y solo consume los eventos cuando el gesto ya es
 * claramente horizontal. Así los toques, la pulsación larga (selección de texto) y el scroll vertical
 * siguen llegando al libro sin cambios.
 *
 * @param onDown el dedo tocó la pantalla (todavía no se sabe si será un arrastre).
 * @param onStart el gesto ya es un arrastre. Devuelve si hay que acompañarlo (false si hay otra animación en curso).
 * @param onDrag avance 0..1 de la animación mientras el dedo sigue abajo.
 * @param onEnd el dedo se soltó: true si se completa el paso de página, y la velocidad del dedo en avance por segundo.
 */
suspend fun PointerInputScope.detectPageSwipes(
    onDown: () -> Unit = {},
    onStart: (direction: PageTurnDirection, progress: Float) -> Boolean,
    onDrag: (progress: Float) -> Unit,
    onEnd: (commit: Boolean, velocity: Float) -> Unit,
) {
    val slop = viewConfiguration.touchSlop
    val width = size.width.toFloat()
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        onDown()
        val velocity = VelocityTracker()
        velocity.addPointerInputChange(down)
        var total = Offset.Zero
        var swiping = false
        var longPress = false
        var accepted = false
        var direction = PageTurnDirection.Forward
        var progress = 0f
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val change = event.changes.firstOrNull { it.id == down.id }
            if (change == null) {
                if (accepted) onEnd(false, 0f)
                return@awaitEachGesture
            }
            velocity.addPointerInputChange(change)
            if (!change.pressed) {
                if (accepted) {
                    val vx = velocity.calculateVelocity().x
                    val towardTurn = if (direction == PageTurnDirection.Forward) -vx else vx
                    onEnd(
                        PageTurnGesture.shouldCommit(progress, towardTurn, density),
                        PageTurnGesture.progressVelocity(towardTurn, width),
                    )
                }
                return@awaitEachGesture
            }
            total += change.positionChange()
            // Pulsación larga: el arrastre selecciona texto y es del navegador (RDR-011).
            if (!swiping && !longPress &&
                PageTurnGesture.isLongPress(change.uptimeMillis - down.uptimeMillis, viewConfiguration.longPressTimeoutMillis)
            ) {
                longPress = true
            }
            if (!swiping && !longPress && abs(total.x) > slop && abs(total.x) > HORIZONTAL_BIAS * abs(total.y)) {
                swiping = true
                direction = if (total.x < 0) PageTurnDirection.Forward else PageTurnDirection.Backward
                progress = PageTurnGesture.progress(total.x, direction, width)
                accepted = onStart(direction, progress)
            }
            if (swiping) {
                change.consume()
                if (accepted) {
                    progress = PageTurnGesture.progress(total.x, direction, width)
                    onDrag(progress)
                }
            }
        }
    }
}

/** El gesto es un arrastre de página si va más de lado que en vertical. Con un sesgo mayor, las diagonales las tomaba Readium. */
private const val HORIZONTAL_BIAS = 1f
