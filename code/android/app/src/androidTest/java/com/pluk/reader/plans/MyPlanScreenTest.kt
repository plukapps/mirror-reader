package com.pluk.reader.plans

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pluk.reader.domain.plans.PlanId
import com.pluk.reader.domain.profile.StorageUsage
import com.pluk.reader.ui.plans.MyPlanContent
import com.pluk.reader.ui.plans.MyPlanUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MyPlanScreenTest {
    @get:Rule
    val compose = createComposeRule()

    // PLN-012: plan Gratis con su espacio, dispositivos y la tarjeta de Lector; "Cambiar de plan" y "Restaurar compras"
    @Test
    fun showsFreePlanAndActions() {
        var back = 0
        var change = 0
        var restore = 0
        compose.setContent {
            MyPlanContent(
                MyPlanUiState(StorageUsage("12 MB", "15 MB", "3 MB", 0.8f, 80)),
                onBack = { back++ },
                onChangePlan = { change++ },
                onRestore = { restore++ },
            )
        }
        compose.onNodeWithText("Mi plan").assertExists()
        compose.onNodeWithText("Tu plan").assertExists()
        compose.onNodeWithContentDescription("Espacio en la nube, 12 MB de 15 MB").assertExists()
        compose.onNodeWithText("Todos, sin límite").assertExists()
        compose.onNodeWithText("Con Lector: 5 GB en la nube").assertExists()
        compose.onNodeWithText("Archivos de hasta 2 MB").assertExists()
        compose.onNodeWithText("Archivos de hasta 50 MB, temas y fuentes.").assertExists()
        compose.onNodeWithTag("my-plan-back").performClick()
        compose.onNodeWithTag("my-plan-change").performClick()
        compose.onNodeWithText("Restaurar compras").performClick()
        assertEquals(Triple(1, 1, 1), Triple(back, change, restore))
    }

    // PLN-017: en Lector, su nombre y máximo, y la oferta de Biblioteca
    @Test
    fun showsPlusPlanWithTheProOffer() {
        compose.setContent {
            MyPlanContent(MyPlanUiState(usage, PlanId.PLUS, 50 * MIB), onBack = {}, onChangePlan = {}, onRestore = {})
        }
        compose.onNodeWithText("Lector").assertExists()
        compose.onNodeWithText("Archivos de hasta 50 MB").assertExists()
        compose.onNodeWithText("Con Biblioteca: 50 GB en la nube").assertExists()
        compose.onNodeWithText("Archivos de hasta 100 MB.").assertExists()
        compose.onNodeWithText("Con Lector: 5 GB en la nube").assertDoesNotExist()
    }

    // PLN-017: en Biblioteca, sin oferta de otro plan
    @Test
    fun showsProPlanWithoutOffer() {
        compose.setContent {
            MyPlanContent(MyPlanUiState(usage, PlanId.PRO, 100 * MIB), onBack = {}, onChangePlan = {}, onRestore = {})
        }
        compose.onNodeWithText("Biblioteca").assertExists()
        compose.onNodeWithText("Tu plan").assertExists()
        compose.onNodeWithText("Archivos de hasta 100 MB").assertExists()
        compose.onNodeWithTag("my-plan-offer").assertDoesNotExist()
    }

    private val usage = StorageUsage("12 MB", "50 GB", "49,9 GB", 0f, 0)

    private companion object {
        const val MIB = 1024L * 1024
    }
}
