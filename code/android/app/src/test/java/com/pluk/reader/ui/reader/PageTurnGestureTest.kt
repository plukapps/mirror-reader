package com.pluk.reader.ui.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PageTurnGestureTest {
    private val width = 1000f

    // RDR-009: el avance es proporcional a lo arrastrado
    @Test
    fun progressGrowsWithTheDragInTheTurnDirection() {
        val half = PageTurnGesture.progress(-width * PageTurnGesture.DRAG_FRACTION / 2, PageTurnDirection.Forward, width)
        assertEquals(0.5f, half, 0.001f)
        assertEquals(1f, PageTurnGesture.progress(-width * PageTurnGesture.DRAG_FRACTION, PageTurnDirection.Forward, width), 0.001f)
    }

    // Retroceder arrastra a la derecha
    @Test
    fun backwardProgressUsesRightwardDrag() {
        assertTrue(PageTurnGesture.progress(300f, PageTurnDirection.Backward, width) > 0f)
        assertEquals(0f, PageTurnGesture.progress(-300f, PageTurnDirection.Backward, width), 0.001f)
    }

    // Si el dedo vuelve más allá del punto de partida, el avance es 0, no negativo
    @Test
    fun draggingBackPastTheStartGivesZero() {
        assertEquals(0f, PageTurnGesture.progress(200f, PageTurnDirection.Forward, width), 0.001f)
    }

    // El avance nunca supera 1
    @Test
    fun progressIsClampedToOne() {
        assertEquals(1f, PageTurnGesture.progress(-5000f, PageTurnDirection.Forward, width), 0.001f)
    }

    // RDR-009: pasado el umbral se completa
    @Test
    fun commitsWhenPastTheThreshold() {
        assertTrue(PageTurnGesture.shouldCommit(PageTurnGesture.COMMIT_PROGRESS + 0.01f, velocityTowardsTurn = 0f, density = 2f))
    }

    // RDR-009: antes del umbral y sin velocidad se cancela
    @Test
    fun cancelsWhenBelowTheThresholdAndSlow() {
        assertFalse(PageTurnGesture.shouldCommit(PageTurnGesture.COMMIT_PROGRESS - 0.1f, velocityTowardsTurn = 100f, density = 2f))
    }

    // RDR-009: una pasada rápida completa aunque el avance sea corto
    @Test
    fun fastFlingCommitsEvenWithShortDrag() {
        val fast = PageTurnGesture.COMMIT_VELOCITY_DP * 2f * 1.5f
        assertTrue(PageTurnGesture.shouldCommit(0.1f, velocityTowardsTurn = fast, density = 2f))
    }

    // Una pasada rápida en sentido contrario nunca completa
    @Test
    fun fastFlingTheOtherWayDoesNotCommit() {
        assertFalse(PageTurnGesture.shouldCommit(0.1f, velocityTowardsTurn = -5000f, density = 2f))
    }

    // Un toque sin movimiento (avance ~0) no completa aunque la velocidad sea alta por ruido
    @Test
    fun noDragNeverCommits() {
        assertFalse(PageTurnGesture.shouldCommit(0f, velocityTowardsTurn = 5000f, density = 2f))
    }

    // La duración de lo que falta es proporcional, con un mínimo para que no sea instantáneo
    @Test
    fun settleDurationIsProportionalWithAMinimum() {
        assertEquals(PageTurnController.DURATION_MS, PageTurnGesture.settleDurationMs(1f))
        assertEquals(230, PageTurnGesture.settleDurationMs(0.5f))
        assertEquals(PageTurnGesture.MIN_SETTLE_MS, PageTurnGesture.settleDurationMs(0f))
    }

    // RDR-009: al soltar, el cierre parte de la velocidad del dedo
    @Test
    fun progressVelocityScalesWithPageWidth() {
        val width = 1000f
        assertEquals(2f, PageTurnGesture.progressVelocity(2f * width * PageTurnGesture.DRAG_FRACTION, width), 1e-4f)
    }

    // RDR-009: la velocidad inicial no empuja al resorte más allá del destino
    @Test
    fun settleVelocityNeverOvershootsTheTarget() {
        val omega = kotlin.math.sqrt(PageTurnGesture.SETTLE_STIFFNESS)
        assertEquals(omega * 0.4f, PageTurnGesture.settleVelocity(100f, 0.6f, commit = true), 1e-3f)
        assertEquals(1.5f, PageTurnGesture.settleVelocity(1.5f, 0.2f, commit = true), 1e-4f)
        assertEquals(0f, PageTurnGesture.settleVelocity(-3f, 0.5f, commit = true), 1e-4f)
        assertEquals(-omega * 0.1f, PageTurnGesture.settleVelocity(-100f, 0.1f, commit = false), 1e-3f)
        assertEquals(0f, PageTurnGesture.settleVelocity(2f, 0.1f, commit = false), 1e-4f)
    }

    // RDR-011: bordes pasan de página, el centro muestra los controles
    @Test
    fun `RDR-011 tap zones`() {
        assertEquals(TapAction.PreviousPage, PageTurnGesture.tapAction(100f, width))
        assertEquals(TapAction.NextPage, PageTurnGesture.tapAction(900f, width))
        assertEquals(TapAction.ToggleControls, PageTurnGesture.tapAction(250f, width))
        assertEquals(TapAction.ToggleControls, PageTurnGesture.tapAction(750f, width))
    }

    // RDR-011: un dedo apoyado el tiempo de pulsación larga no pasa de página
    @Test
    fun `RDR-011 long press is not a swipe`() {
        assertFalse(PageTurnGesture.isLongPress(200, 400))
        assertTrue(PageTurnGesture.isLongPress(400, 400))
    }
}
