package com.pluk.reader.reader

import com.pluk.reader.domain.pageLabel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

// RDR-010, RDR-016: texto del pie
class PageLabelTest {
    @Test fun singlePageShowsPosition() = assertEquals("107", pageLabel(107, false, 254))
    @Test fun spreadShowsPair() = assertEquals("107–108", pageLabel(107, true, 254))
    @Test fun spreadOnLastPositionShowsOne() = assertEquals("254", pageLabel(254, true, 254))
    @Test fun spreadOnFirstShowsPair() = assertEquals("1–2", pageLabel(1, true, 254))
    @Test fun spreadWithUnknownLastShowsPair() = assertEquals("5–6", pageLabel(5, true, null))
    @Test fun noPositionShowsNothing() = assertNull(pageLabel(null, true, 254))
}
