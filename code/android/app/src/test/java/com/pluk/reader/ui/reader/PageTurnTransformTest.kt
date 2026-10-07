package com.pluk.reader.ui.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PageTurnTransformTest {
    private val width = 1000f

    // RDR-009: al empezar, la página actual está en su sitio y la nueva está desplazada a la derecha
    @Test
    fun forwardStartsWithCurrentPageInPlaceAndNextOffsetRight() {
        val t = PageTurnTransform.at(0f, PageTurnDirection.Forward, width)
        assertEquals(0f, t.outgoingTranslationX, 0.001f)
        assertTrue(t.incomingTranslationX > 0f)
    }

    // RDR-009: al terminar, la actual salió por la izquierda y la nueva está en su sitio
    @Test
    fun forwardEndsWithCurrentPageGoneLeftAndNextInPlace() {
        val t = PageTurnTransform.at(1f, PageTurnDirection.Forward, width)
        assertEquals(-width, t.outgoingTranslationX, 0.001f)
        assertEquals(0f, t.incomingTranslationX, 0.001f)
    }

    // RDR-009: el paralaje es pequeño (mucho menos que el ancho de la página)
    @Test
    fun parallaxOffsetIsSmall() {
        val t = PageTurnTransform.at(0f, PageTurnDirection.Forward, width)
        assertTrue(t.incomingTranslationX in 1f..(width * 0.3f))
    }

    // RDR-009: retroceder es el avance reproducido al revés. La que entra va encima y repite, en sentido
    // contrario, lo que hacía la que salía (posición y aclarado); la de abajo hace el paralaje.
    @Test
    fun backwardIsTheForwardRolledBack() {
        for (p in listOf(0f, 0.25f, 0.5f, 0.95f, 1f)) {
            val f = PageTurnTransform.at(1f - p, PageTurnDirection.Forward, width)
            val b = PageTurnTransform.at(p, PageTurnDirection.Backward, width)
            assertEquals(f.outgoingTranslationX, b.incomingTranslationX, 0.001f)
            assertEquals(f.outgoingLightenAmount, b.incomingLightenAmount, 0.001f)
            assertEquals(f.incomingTranslationX, b.outgoingTranslationX, 0.001f)
        }
    }

    // RDR-009: al retroceder, la página anterior empieza fuera por la izquierda y termina en su sitio, ya sin aclarar
    @Test
    fun backwardStartsWithPreviousPageOffLeftAndEndsInPlace() {
        val start = PageTurnTransform.at(0f, PageTurnDirection.Backward, width)
        assertEquals(-width, start.incomingTranslationX, 0.001f)
        assertEquals(0f, start.outgoingTranslationX, 0.001f)
        val end = PageTurnTransform.at(1f, PageTurnDirection.Backward, width)
        assertEquals(0f, end.incomingTranslationX, 0.001f)
        assertEquals(0f, end.incomingLightenAmount, 0.001f)
        assertTrue(end.outgoingTranslationX in 1f..(width * 0.3f))
    }

    // RDR-009: el aclarado va a la página que entra al retroceder, y a la que sale al avanzar
    @Test
    fun lightenAppliesToTheTopLayer() {
        val f = PageTurnTransform.at(1f, PageTurnDirection.Forward, width)
        assertEquals(1f, f.outgoingLightenAmount, 0.001f)
        assertEquals(0f, f.incomingLightenAmount, 0.001f)
        val b = PageTurnTransform.at(0f, PageTurnDirection.Backward, width)
        assertEquals(1f, b.incomingLightenAmount, 0.001f)
        assertEquals(0f, b.outgoingLightenAmount, 0.001f)
    }

    // RDR-009: el aclarado es gradual (empieza en 0, sin salto) y el cambio se nota más al final que al comienzo
    @Test
    fun lightenIsGradualAndGrowsFasterTowardTheEnd() {
        assertEquals(0f, PageTurnTransform.at(0f, PageTurnDirection.Forward, width).outgoingLightenAmount, 0.001f)
        var last = 0f
        var lastStep = 0f
        for (i in 1..10) {
            val l = PageTurnTransform.at(i / 10f, PageTurnDirection.Forward, width).outgoingLightenAmount
            assertTrue(l - last >= lastStep - 0.0001f)
            lastStep = l - last
            last = l
        }
        assertTrue(PageTurnTransform.at(0.5f, PageTurnDirection.Forward, width).outgoingLightenAmount < 0.5f)
    }

    // El paralaje solo se reduce a medida que avanza la animación
    @Test
    fun parallaxOnlyShrinks() {
        var last = PageTurnTransform.at(0f, PageTurnDirection.Forward, width)
        for (i in 1..10) {
            val t = PageTurnTransform.at(i / 10f, PageTurnDirection.Forward, width)
            assertTrue(t.incomingTranslationX <= last.incomingTranslationX)
            assertTrue(t.outgoingTranslationX <= last.outgoingTranslationX)
            last = t
        }
    }

    // Un progreso fuera de rango no debe sacar las capas de su sitio
    @Test
    fun progressIsClamped() {
        assertEquals(PageTurnTransform.at(1f, PageTurnDirection.Forward, width), PageTurnTransform.at(7f, PageTurnDirection.Forward, width))
        assertEquals(PageTurnTransform.at(0f, PageTurnDirection.Forward, width), PageTurnTransform.at(-3f, PageTurnDirection.Forward, width))
    }
}
