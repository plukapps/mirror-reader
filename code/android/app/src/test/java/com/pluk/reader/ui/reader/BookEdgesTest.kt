package com.pluk.reader.ui.reader

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BookEdgesTest {
    private var clock = 0L
    private val edges = BookEdges<String>(now = { clock })

    // RDR-009: en la última página no se anima nada, así que el borde descubierto bloquea ese paso
    @Test
    fun blockedEdgeRefusesTheSameStepFromTheSamePage() {
        edges.block(forward = true, at = "p10")
        assertTrue(edges.isBlocked(forward = true, at = "p10"))
        assertFalse(edges.isBlocked(forward = false, at = "p10"))
        assertFalse(edges.isBlocked(forward = true, at = "p11"))
    }

    // K-044: un falso final (el navegador no respondió) no puede bloquear la página para siempre
    @Test
    fun blockExpires() {
        edges.block(forward = true, at = "p10")
        clock = BookEdges.BLOCK_TTL_MS - 1
        assertTrue(edges.isBlocked(forward = true, at = "p10"))
        clock = BookEdges.BLOCK_TTL_MS
        assertFalse(edges.isBlocked(forward = true, at = "p10"))
    }
}
