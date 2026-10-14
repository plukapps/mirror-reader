package com.pluk.reader.notes

import com.pluk.reader.domain.model.Note
import org.junit.Assert.assertEquals
import org.junit.Test

class NoteTest {
    private fun at(progression: Double) = Note("n", "b", "{}", "q", "t", progression, 0, 0)

    // ANN-010: el porcentaje del libro donde está la nota
    @Test
    fun percentOfTheBook() {
        assertEquals(0, at(0.0).percent)
        assertEquals(42, at(0.427).percent)
        assertEquals(100, at(1.0).percent)
        assertEquals(100, at(1.3).percent)
    }
}
