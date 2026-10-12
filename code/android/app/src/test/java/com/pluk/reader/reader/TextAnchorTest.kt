package com.pluk.reader.reader

import com.pluk.reader.domain.TextAnchor
import com.pluk.reader.domain.textAnchorAt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * SYN-014: la posición recuerda el texto del comienzo de la página. Mismos casos que `TextAnchorTests` de Apple.
 */
class TextAnchorTest {
    private val before = "a".repeat(40)
    private val highlight = "b".repeat(32)
    private val after = "c".repeat(40)

    @Test
    fun takesThirtyTwoCharactersOfContextOnEachSide() {
        val anchor = textAnchorAt(before + highlight + after, start = 40)
        assertEquals(TextAnchor(before = "a".repeat(32), highlight = highlight, after = "c".repeat(32)), anchor)
    }

    // El primer carácter visible puede ser un espacio o un salto de línea entre párrafos
    @Test
    fun startsAtTheFirstVisibleCharacterAfterWhitespace() {
        val anchor = textAnchorAt("fin.\n  \tY entonces la puerta", start = 4)
        assertEquals("Y entonces la puerta", anchor?.highlight)
        assertEquals("fin.\n  \t", anchor?.before)
        assertNull(anchor?.after)
    }

    @Test
    fun atTheStartOfTheChapterThereIsNoBefore() {
        val anchor = textAnchorAt("Capítulo uno", start = 0)
        assertEquals(TextAnchor(before = null, highlight = "Capítulo uno", after = null), anchor)
    }

    @Test
    fun withoutVisibleTextThereIsNoAnchor() {
        assertNull(textAnchorAt("texto   \n ", start = 5))
        assertNull(textAnchorAt("texto", start = 5))
        assertNull(textAnchorAt("texto", start = -1))
        assertNull(textAnchorAt("", start = 0))
    }

    // Los cortes no parten un emoji (par sustituto UTF-16): el texto tiene que poder encontrarse tal cual
    @Test
    fun cutsDoNotSplitSurrogatePairs() {
        // Los cortes de 32 caerían en la mitad de cada emoji: se corren un carácter hacia adelante.
        val window = "😀" + "a".repeat(31) + "b".repeat(31) + "😀" + "c"
        val anchor = textAnchorAt(window, start = 33)!!
        assertEquals("a".repeat(31), anchor.before)
        assertEquals("b".repeat(31) + "😀", anchor.highlight)
        assertEquals("c", anchor.after)
    }
}
