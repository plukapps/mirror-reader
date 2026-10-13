package com.pluk.reader.plans

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
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
        compose.onNodeWithTag("my-plan-back").performClick()
        compose.onNodeWithTag("my-plan-change").performClick()
        compose.onNodeWithText("Restaurar compras").performClick()
        assertEquals(Triple(1, 1, 1), Triple(back, change, restore))
    }
}
