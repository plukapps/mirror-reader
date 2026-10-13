package com.pluk.reader.plans

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pluk.reader.domain.plans.ImportSource
import com.pluk.reader.domain.plans.PaidFeature
import com.pluk.reader.domain.plans.PlanPrompt
import com.pluk.reader.ui.plans.ImportSheetContent
import com.pluk.reader.ui.plans.PlanPromptContent
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ImportSheetTest {
    @get:Rule
    val compose = createComposeRule()

    // PLN-010: tres orígenes; Drive y Calibre con la marca "Biblioteca"
    @Test
    fun listsSourcesWithPaidBadges() {
        val chosen = mutableListOf<ImportSource>()
        compose.setContent { ImportSheetContent(onSource = { chosen += it }) }
        compose.onNodeWithText("Importar libros").assertExists()
        compose.onNodeWithContentDescription("Desde este dispositivo").performClick()
        compose.onNodeWithContentDescription("Google Drive, incluido en Biblioteca").performClick()
        compose.onNodeWithContentDescription("Calibre / OPDS, incluido en Biblioteca").performClick()
        assertEquals(listOf(ImportSource.DEVICE, ImportSource.DRIVE, ImportSource.CALIBRE), chosen)
    }

    // PLN-010: hoja de función paga de Drive
    @Test
    fun paidSheetForDrive() {
        var seePlans = 0
        compose.setContent { PlanPromptContent(PlanPrompt.Paid(PaidFeature.DRIVE), onSeePlans = { seePlans++ }, onNotNow = {}) }
        compose.onNodeWithText("Traé tus libros desde Drive").assertExists()
        compose.onNodeWithText("Incluido en Biblioteca").assertExists()
        compose.onNodeWithText("Ver planes").performClick()
        assertEquals(1, seePlans)
    }
}
