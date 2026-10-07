package com.pluk.reader.ui.reader

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Reglas del gesto de arrastrar para pasar de página (RDR-009), sin Android para poder probarlas.
 */
object PageTurnGesture {
    /** Fracción del ancho que hay que arrastrar para completar la animación. */
    const val DRAG_FRACTION = 0.85f

    /** Avance mínimo con el que, al soltar, la animación se completa. */
    const val COMMIT_PROGRESS = 0.3f

    /** Velocidad (dp/s) a partir de la cual una pasada rápida completa aunque el avance sea corto. */
    const val COMMIT_VELOCITY_DP = 800f

    /** Una pasada rápida solo cuenta si el dedo avanzó algo: así el ruido de un toque no pasa de página. */
    const val MIN_PROGRESS_FOR_FLING = 0.03f

    /** Por debajo de este avance la sesión se trata como un toque y no como un arrastre. */
    const val MIN_PROGRESS_TO_FOLLOW = 0.02f

    /** Fracción del ancho, a cada lado, donde un toque pasa de página (RDR-011). El resto muestra los controles. */
    const val EDGE_FRACTION = 0.2f

    /** Qué hace un toque en [x] (px) sobre una página de ancho [width]. */
    fun tapAction(x: Float, width: Float): TapAction = when {
        x < width * EDGE_FRACTION -> TapAction.PreviousPage
        x > width * (1f - EDGE_FRACTION) -> TapAction.NextPage
        else -> TapAction.ToggleControls
    }

    /**
     * Un dedo que ya llevaba apoyado el tiempo de pulsación larga está seleccionando texto: su arrastre
     * es del navegador y no pasa de página (RDR-011).
     */
    fun isLongPress(heldMs: Long, longPressTimeoutMs: Long): Boolean = heldMs >= longPressTimeoutMs

    /** Rigidez del resorte de cierre. Con amortiguación crítica no rebota; `sqrt(rigidez)` es su frecuencia (1/s). */
    const val SETTLE_STIFFNESS = 400f

    /** Duración mínima de lo que falta para completar o cancelar, para que no sea un salto. */
    const val MIN_SETTLE_MS = 160

    /** Resorte con el que termina la animación al soltar: sin rebote, frena suavemente. */
    fun settleSpring() = spring<Float>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = SETTLE_STIFFNESS,
        visibilityThreshold = 0.002f,
    )

    /**
     * Velocidad con la que arranca el cierre (avance por segundo): la del dedo, limitada para que el resorte
     * no se pase del destino y tenga que frenar de golpe. Nunca va en contra del destino por más de lo que
     * el dedo se alejó.
     *
     * @param velocity velocidad del dedo, positiva hacia completar el giro.
     * @param progress avance actual de la animación.
     */
    fun settleVelocity(velocity: Float, progress: Float, commit: Boolean): Float {
        val omega = sqrt(SETTLE_STIFFNESS)
        return if (commit) {
            velocity.coerceIn(0f, omega * (1f - progress))
        } else {
            velocity.coerceIn(-omega * progress, 0f)
        }
    }

    /** Velocidad del dedo en avance por segundo, a partir de [velocityPx] (px/s hacia el giro). */
    fun progressVelocity(velocityPx: Float, pageWidth: Float): Float = velocityPx / (pageWidth * DRAG_FRACTION)

    /** Avance 0..1 de la animación según el desplazamiento horizontal [dx] del dedo (px). */
    fun progress(dx: Float, direction: PageTurnDirection, pageWidth: Float): Float {
        val towardTurn = if (direction == PageTurnDirection.Forward) -dx else dx
        return (towardTurn / (pageWidth * DRAG_FRACTION)).coerceIn(0f, 1f)
    }

    /**
     * Al soltar: ¿se completa el paso de página o se cancela?
     *
     * @param velocityTowardsTurn velocidad del dedo (px/s) en el sentido del giro. Negativa si va al revés.
     */
    fun shouldCommit(progress: Float, velocityTowardsTurn: Float, density: Float): Boolean =
        progress >= COMMIT_PROGRESS ||
            (progress >= MIN_PROGRESS_FOR_FLING && velocityTowardsTurn >= COMMIT_VELOCITY_DP * density)

    /** Duración de lo que falta de animación, proporcional a la distancia [remaining] (0..1). */
    fun settleDurationMs(remaining: Float): Int =
        (MIN_SETTLE_MS + (PageTurnController.DURATION_MS - MIN_SETTLE_MS) * remaining.coerceIn(0f, 1f)).roundToInt()
}

/** Resultado de un toque sobre la página (RDR-011). */
enum class TapAction { PreviousPage, NextPage, ToggleControls }
