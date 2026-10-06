package com.pluk.reader.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TocAndProgressTest {
    // RDR-005
    @Test
    fun progressIsRoundedPercent() {
        assertEquals(42, progressPercent(0.4249))
        assertEquals(0, progressPercent(0.0))
        assertEquals(100, progressPercent(1.0))
    }

    // RDR-005: sin dato todavía
    @Test
    fun progressIsNullWhenUnknown() {
        assertNull(progressPercent(null))
    }

    // RDR-005: valores fuera de rango
    @Test
    fun progressIsClamped() {
        assertEquals(100, progressPercent(1.7))
        assertEquals(0, progressPercent(-0.2))
    }

    // Foco de revisión 3: libro sin tabla de contenidos
    @Test
    fun emptyTocFlattensToEmptyList() {
        assertTrue(flattenToc(emptyList()).isEmpty())
    }
}
