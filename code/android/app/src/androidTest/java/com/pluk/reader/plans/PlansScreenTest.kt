package com.pluk.reader.plans

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pluk.reader.domain.plans.Billing
import com.pluk.reader.domain.plans.PlanId
import com.pluk.reader.domain.profile.StorageUsage
import com.pluk.reader.ui.plans.PlansActions
import com.pluk.reader.ui.plans.PlansContent
import com.pluk.reader.ui.plans.PlansUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlansScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val usage = StorageUsage("12 MB", "15 MB", "3 MB", 0.8f, 80)

    /** Planes con el estado real en memoria, como lo maneja el ViewModel. */
    private fun show(initial: PlansUiState = PlansUiState(usage = usage), onComingSoon: () -> Unit = {}) {
        compose.setContent {
            var state by remember { mutableStateOf(initial) }
            PlansContent(
                state,
                PlansActions(
                    onBilling = { state = state.copy(billing = it) },
                    onSelect = { if (it != PlanId.FREE) state = state.copy(selected = it) },
                    onToggleTable = { state = state.copy(tableOpen = !state.tableOpen) },
                    onComingSoon = onComingSoon,
                ),
            )
        }
    }

    // PLN-003: uso de la cuenta y título
    @Test
    fun showsUsageAndTitle() {
        show()
        compose.onNodeWithContentDescription("Usás 12 MB de 15 MB, 80 %").assertExists()
        compose.onNodeWithText("Tu biblioteca entera, en todos lados.").assertExists()
    }

    // PLN-003: sin cuota leída no hay fila de uso
    @Test
    fun hidesUsageWithoutQuota() {
        show(PlansUiState())
        compose.onNodeWithTag("plans-usage").assertDoesNotExist()
    }

    // PLN-004, PLN-006: arranca en anual, sin prueba gratis; en mensual cambia el botón
    @Test
    fun billingChangesTheButton() {
        show()
        compose.onNodeWithTag("billing-ANNUAL").assertIsSelected()
        compose.onNodeWithTag("plans-cta").assertExists()
        compose.onNodeWithText("Elegir Lector · $24.99 por año").assertExists()
        compose.onNodeWithText("Se renueva cada año. Cancelás cuando quieras.").assertExists()

        compose.onNodeWithTag("billing-MONTHLY").performClick()
        compose.onNodeWithText("Elegir Lector · $2.99 por mes").assertExists()
        compose.onNodeWithText("Se renueva cada mes. Cancelás cuando quieras.").assertExists()
    }

    // PLN-005, PLN-013: Lector elegido al empezar; tocar Biblioteca la elige
    @Test
    fun selectingLibraryChangesTheButton() {
        show()
        compose.onNodeWithTag("plan-READER").assertIsSelected()
        compose.onNodeWithTag("plan-LIBRARY").performScrollTo().performClick()
        compose.onNodeWithTag("plan-LIBRARY").assertIsSelected()
        compose.onNodeWithTag("plan-READER").assertIsNotSelected()
        compose.onNodeWithText("Elegir Biblioteca · $49.99 por año").assertExists()
    }

    // PLN-006: la tabla se despliega y se lee por fila
    @Test
    fun tableOpensAndReadsByRow() {
        show()
        compose.onNodeWithTag("plans-table").assertDoesNotExist()
        compose.onNodeWithTag("plans-see-all").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Espacio en la nube: Gratis, 15 MB; Lector, 5 GB; Biblioteca, 50 GB")
            .performScrollTo().assertExists()
        compose.onNodeWithContentDescription("Tamaño por archivo: Gratis, 2 MB; Lector, 50 MB; Biblioteca, 100 MB")
            .performScrollTo().assertExists()
        // PLN-016: sin funciones en definición
        compose.onNodeWithText("Familia (hasta 5 cuentas)").assertDoesNotExist()
    }

    // PLN-006, PRF-003: comprar y "Restaurar compras" avisan
    @Test
    fun purchaseActionsShowComingSoon() {
        var notices = 0
        show(onComingSoon = { notices++ })
        compose.onNodeWithTag("plans-cta").performClick()
        compose.onNodeWithText("Restaurar compras").performScrollTo().performClick()
        assertEquals(2, notices)
    }
}
