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
        compose.onNodeWithContentDescription("Free, 15 MB").assertExists()
        compose.onNodeWithContentDescription("Plus, 5 GB").assertExists()
        compose.onNodeWithText("Ver planes").performClick()
        compose.onNodeWithText("Ahora no").performClick()
        assertEquals(1 to 1, seePlans to notNow)
    }

    // PLN-008: queda espacio pero el libro no entra
    @Test
    fun doesNotFitSheetShowsSizes() {
        show(PlanPrompt.Quota("Atlas", QuotaBlock.DoesNotFit(2 * MIB, 1 * MIB), 15 * MIB))
        compose.onNodeWithText("Este libro pesa 2 MB").assertExists()
        compose.onNodeWithText("Te quedan 1 MB en la nube. No se subió, pero podés leerlo en este dispositivo.").assertExists()
        compose.onNodeWithText("2 MB · te quedan 1 MB").assertExists()
        compose.onNodeWithText("Con Plus: 5 GB en la nube").assertExists()
    }

    // PLN-015: hoja de función paga de temas y fuentes
    @Test
    fun paidSheetForThemes() {
        show(PlanPrompt.Paid(com.pluk.reader.domain.plans.PaidFeature.THEMES_FONTS))
        compose.onNodeWithText("Leé con tus temas y fuentes").assertExists()
        compose.onNodeWithText("Sepia, Noche y todas las letras del lector, en todos tus libros.").assertExists()
        compose.onNodeWithText("Incluido en Plus").assertExists()
        compose.onNodeWithText("Ver planes").performClick()
        assertEquals(1, seePlans)
    }

    // PLN-014: archivo más grande que el máximo del plan
    @Test
    fun tooLargeSheetShowsTheLimit() {
        show(PlanPrompt.Quota("Atlas", QuotaBlock.TooLarge(3 * MIB, 2 * MIB, com.pluk.reader.domain.plans.PlanId.FREE), 15 * MIB))
        compose.onNodeWithText("3 MB · límite 2 MB").assertExists()
        compose.onNodeWithText("Este libro pesa 3 MB").assertExists()
        compose.onNodeWithText("Tu plan admite archivos de hasta 2 MB. No se subió a la nube, pero podés leerlo en este dispositivo.").assertExists()
        compose.onNodeWithText("Con Plus: archivos de hasta 50 MB y 5 GB en la nube").assertExists()
    }
}
