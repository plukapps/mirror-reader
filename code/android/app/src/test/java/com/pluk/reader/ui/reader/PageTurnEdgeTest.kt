package com.pluk.reader.ui.reader

import android.graphics.Bitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** RDR-009: en los bordes del libro no se inicia ninguna sesión de animación. */
class PageTurnEdgeTest {
    private class FakeOps(private val allowed: Boolean) : PageTurnOps {
        var moves = 0
        override suspend fun capture(): Bitmap? = null
        override suspend fun captureIfReady(): Bitmap? = null
        override fun canMove(direction: PageTurnDirection) = allowed
        override fun move(direction: PageTurnDirection): Boolean { moves++; return true }
        override suspend fun awaitMoved() = true
    }

    @Test
    fun `RDR-009 sin mas paginas no se inicia la sesion ni se mueve el navegador`() {
        val controller = PageTurnController(CoroutineScope(Job()))
        val ops = FakeOps(allowed = false)

        assertFalse(controller.begin(PageTurnDirection.Backward, 0f, ops))

        assertFalse(controller.busy)
        assertTrue(ops.moves == 0)
    }
}
