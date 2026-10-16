package com.pluk.reader.welcome

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.hasText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pluk.reader.ui.welcome.GuestReadyScreen
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** GST-019: "Listo" sin cuenta (L0). */
@RunWith(AndroidJUnit4::class)
class GuestReadyScreenTest {
    @get:Rule
    val compose = createComposeRule()

    // GST-019: textos del diseño, sin libros de regalo; GST-018: el título se anuncia como encabezado
    @Test
    fun showsTheTextsAndAHeading() {
        compose.setContent { GuestReadyScreen(onStartReading = {}, onImportBooks = {}) }
        compose.onNode(isHeading().and(hasText("Ya podés\nleer."))).assertExists()
        compose.onNodeWithText("Empezá importando tus libros. Todo queda en este dispositivo, sin cuenta.").assertExists()
        compose.onNodeWithTag("guest-ready-start").assertHasClickAction()
        compose.onNodeWithTag("guest-ready-import").assertHasClickAction()
        // Las portadas son ilustración: el lector de pantalla no las anuncia
        compose.onNodeWithText("Letters from a Stoic").assertDoesNotExist()
    }

    // GST-019: "Empezar a leer" e "Importar mis libros"
    @Test
    fun buttonsLeadToHomeAndImport() {
        var start = 0
        var import = 0
        compose.setContent { GuestReadyScreen(onStartReading = { start++ }, onImportBooks = { import++ }) }
        compose.onNodeWithText("Empezar a leer").performClick()
        compose.onNodeWithText("Importar mis libros").performClick()
        compose.waitForIdle()
        assertEquals(listOf(1, 1), listOf(start, import))
    }
}
