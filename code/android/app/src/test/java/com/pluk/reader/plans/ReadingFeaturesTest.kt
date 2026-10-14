package com.pluk.reader.plans

import com.pluk.reader.domain.model.LineSpacing
import com.pluk.reader.domain.model.ReaderFont
import com.pluk.reader.domain.model.ReaderSettings
import com.pluk.reader.domain.model.ReadingTheme
import com.pluk.reader.domain.plans.PaidFeature
import com.pluk.reader.domain.plans.PlanId
import com.pluk.reader.domain.plans.PlanPrompt
import com.pluk.reader.domain.plans.PlanPrompts
import com.pluk.reader.domain.plans.allowReadingOption
import com.pluk.reader.domain.plans.effectiveReaderSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** PLN-015: temas y fuentes solo en Lector y Biblioteca. */
class ReadingFeaturesTest {
    private val saved = ReaderSettings(theme = ReadingTheme.SEPIA, font = ReaderFont.MONO, fontScale = 1.3, lineSpacing = LineSpacing.WIDE)

    @Test
    fun freeReadsWithClassicAndSerifKeepingSizeAndSpacing() {
        assertEquals(
            ReaderSettings(theme = ReadingTheme.LIGHT, font = ReaderFont.SERIF, fontScale = 1.3, lineSpacing = LineSpacing.WIDE),
            effectiveReaderSettings(saved, PlanId.FREE),
        )
    }

    @Test
    fun paidPlansKeepWhatWasSaved() {
        assertEquals(saved, effectiveReaderSettings(saved, PlanId.PLUS))
        assertEquals(saved, effectiveReaderSettings(saved, PlanId.PRO))
    }

    @Test
    fun paidOptionsOnFreeAskForThePlan() {
        val prompts = PlanPrompts()
        assertTrue(prompts.allowReadingOption(PlanId.FREE, paidOption = false))
        assertNull(prompts.current.value)
        assertFalse(prompts.allowReadingOption(PlanId.FREE, paidOption = true))
        assertEquals(PlanPrompt.Paid(PaidFeature.THEMES_FONTS), prompts.current.value)
    }

    @Test
    fun paidOptionsOnReaderAreAllowed() {
        val prompts = PlanPrompts()
        assertTrue(prompts.allowReadingOption(PlanId.PLUS, paidOption = true))
        assertNull(prompts.current.value)
    }
}
