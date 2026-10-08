package com.pluk.reader.reader

import com.pluk.reader.domain.useTwoPages
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// RDR-016, AND-006: cuándo el lector muestra dos páginas
class TwoPagesTest {
    @Test fun tabletLandscapeShowsTwo() = assertTrue(useTwoPages(1194, 834))
    @Test fun tabletPortraitShowsOne() = assertFalse(useTwoPages(834, 1194))
    @Test fun bigTabletPortraitShowsOne() = assertFalse(useTwoPages(1024, 1366))
    @Test fun phonePortraitShowsOne() = assertFalse(useTwoPages(412, 915))
    @Test fun phoneLandscapeShowsOne() = assertFalse(useTwoPages(915, 412))
    @Test fun narrowWindowShowsOne() = assertFalse(useTwoPages(700, 600))
    @Test fun justAboveThresholdsShowsTwo() = assertTrue(useTwoPages(841, 480))
    @Test fun atThresholdsShowsTwo() = assertTrue(useTwoPages(840, 480))
    @Test fun justBelowWidthShowsOne() = assertFalse(useTwoPages(839, 480))
    @Test fun justBelowHeightShowsOne() = assertFalse(useTwoPages(1000, 479))
    @Test fun squareWindowShowsOne() = assertFalse(useTwoPages(840, 840))
}
