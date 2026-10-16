package com.pluk.reader.profile

import com.pluk.reader.domain.help.Faq
import com.pluk.reader.domain.help.FaqId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FaqTest {
    // PRF-017: la tarjeta de Perfil muestra estas tres, en este orden
    @Test
    fun cardShowsTheThreeQuestionsOfTheDesign() {
        assertEquals(listOf(FaqId.EPUB, FaqId.BOOKS, FaqId.SPACE), Faq.card)
    }

    // PRF-018: la lista completa trae todas las preguntas, una vez cada una, empezando por las de la tarjeta
    @Test
    fun allQuestionsStartWithTheCardOnes() {
        assertEquals(FaqId.entries.size, Faq.all.toSet().size)
        assertEquals(Faq.all.size, Faq.all.toSet().size)
        assertEquals(Faq.card, Faq.all.take(Faq.card.size))
    }

    // PRF-018: las preguntas se pueden abrir por su id (navegación)
    @Test
    fun findsByKey() {
        Faq.all.forEach { assertEquals(it, Faq.byKey(it.key)) }
        assertTrue(Faq.byKey("no-existe") == null)
    }
}
