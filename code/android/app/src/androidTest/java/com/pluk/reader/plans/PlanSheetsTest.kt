package com.pluk.reader.plans

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pluk.reader.domain.plans.PlanPrompt
import com.pluk.reader.domain.plans.QuotaBlock
import com.pluk.reader.ui.plans.PlanPromptContent
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val MIB = 1024L * 1024

@RunWith(AndroidJUnit4::class)
class PlanSheetsTest {
    @get:Rule
    val compose = createComposeRule()

    private var seePlans = 0
    private var notNow = 0

    private fun show(prompt: PlanPrompt) = compose.setContent {
        PlanPromptContent(prompt, onSeePlans = { seePlans++ }, onNotNow = { notNow++ })
    }

    // PLN-007: espacio lleno
    @Test
    fun fullSheetShowsTitleBookAndComparison() {
        show(PlanPrompt.Quota("Dune", QuotaBlock.Full, 15 * MIB))
        compose.onNodeWithText("Llegaste a tu espacio").assertExists()
        compose.onNodeWithText(
            "Dune no se subió a la nube, pero podés leerlo en este dispositivo. Tus otros libros siguen intactos.",
        ).assertExists()
        compose.onNodeWithContentDescription("Gratis, 15 MB").assertExists()
        compose.onNodeWithContentDescription("Lector, 5 GB").assertExists()
        compose.onNodeWithText("Ver planes").performClick()
        compose.onNodeWithText("Ahora no").performClick()
        assertEquals(1 to 1, seePlans to notNow)
    }

    // PLN-008: queda espacio pero el libro no entra
    @Test
    fun doesNotFitSheetShowsSizes() {
        show(PlanPrompt.Quota("Atlas", QuotaBlock.DoesNotFit(8 * MIB, 3 * MIB), 15 * MIB))
        compose.onNodeWithText("Este libro pesa 8 MB").assertExists()
        compose.onNodeWithText("Te quedan 3 MB en la nube. No se subió, pero podés leerlo en este dispositivo.").assertExists()
        compose.onNodeWithText("8 MB · te quedan 3 MB").assertExists()
        compose.onNodeWithText("Con Lector: 5 GB en la nube").assertExists()
    }
}
