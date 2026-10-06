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
        assertEquals(1f, t.outgoingAlpha, 0.001f)
        assertTrue(t.incomingTranslationX > 0f)
    }

    // RDR-009: al terminar, la actual salió por la izquierda con menos opacidad y la nueva está en su sitio
    @Test
    fun forwardEndsWithCurrentPageGoneLeftAndNextInPlace() {
        val t = PageTurnTransform.at(1f, PageTurnDirection.Forward, width)
        assertEquals(-width, t.outgoingTranslationX, 0.001f)
        assertTrue(t.outgoingAlpha < 1f && t.outgoingAlpha > 0.5f)
        assertEquals(0f, t.incomingTranslationX, 0.001f)
    }

    // RDR-009: el paralaje es pequeño (mucho menos que el ancho de la página)
    @Test
    fun parallaxOffsetIsSmall() {
        val t = PageTurnTransform.at(0f, PageTurnDirection.Forward, width)
        assertTrue(t.incomingTranslationX in 1f..(width * 0.3f))
    }

    // RDR-009: retroceder es lo mismo en sentido inverso (espejo)
    @Test
    fun backwardIsTheMirrorOfForward() {
        for (p in listOf(0f, 0.25f, 0.5f, 1f)) {
            val f = PageTurnTransform.at(p, PageTurnDirection.Forward, width)
            val b = PageTurnTransform.at(p, PageTurnDirection.Backward, width)
            assertEquals(-f.outgoingTranslationX, b.outgoingTranslationX, 0.001f)
            assertEquals(f.outgoingAlpha, b.outgoingAlpha, 0.001f)
            assertEquals(-f.incomingTranslationX, b.incomingTranslationX, 0.001f)
        }
    }

    // La opacidad solo baja y el paralaje solo se reduce a medida que avanza la animación
    @Test
    fun alphaOnlyDecreasesAndParallaxOnlyShrinks() {
        var last = PageTurnTransform.at(0f, PageTurnDirection.Forward, width)
        for (i in 1..10) {
            val t = PageTurnTransform.at(i / 10f, PageTurnDirection.Forward, width)
            assertTrue(t.outgoingAlpha <= last.outgoingAlpha)
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
