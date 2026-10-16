package com.pluk.reader.actions

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.pluk.reader.ui.actions.placeMenu
import org.junit.Assert.assertEquals
import org.junit.Test

/** LIB-016: la tarjeta va sobre la portada, centrada, y nunca sale de la pantalla. */
class MenuPlacementTest {
    private val screen = IntSize(1000, 2000)
    private val menu = IntSize(600, 700)

    private fun place(anchor: Rect) = placeMenu(anchor, menu, screen, gap = 40, margin = 50, safeTop = 100, safeBottom = 150)

    @Test
    fun aboveTheCoverAndCentered() {
        assertEquals(IntOffset(200, 1160), place(Rect(400f, 1900f, 600f, 1950f)))
    }

    @Test
    fun belowWhenThereIsNoRoomAbove() {
        assertEquals(IntOffset(200, 540), place(Rect(400f, 200f, 600f, 500f)))
    }

    @Test
    fun clampedToTheSidesWithMargin() {
        assertEquals(50, place(Rect(0f, 1200f, 200f, 1500f)).x)
        assertEquals(350, place(Rect(800f, 1200f, 1000f, 1500f)).x)
    }

    @Test
    fun neverBelowTheSafeAreaWhenNeitherSideFits() {
        // Portada alta en el medio: no entra arriba ni abajo, queda pegada al borde inferior seguro.
        assertEquals(2000 - 150 - 50 - 700, place(Rect(400f, 600f, 600f, 1500f)).y)
    }
}
