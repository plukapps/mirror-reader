package com.pluk.reader.welcome

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pluk.reader.ui.welcome.WelcomeScreen
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WelcomeScreenTest {
    @get:Rule
    val compose = createComposeRule()

    // GST-001: el contenido del diseño O1 v2
    @Test
    fun showsTheDesignContent() {
        compose.setContent { WelcomeScreen(onStartReading = {}, onCreateAccount = {}, onSignIn = {}) }
        compose.onNodeWithTag("welcome-headline").assertExists()
        compose.onNodeWithText("Tus EPUB, en un lugar tranquilo, en todos tus dispositivos.").assertExists()
        compose.onNodeWithText("Empezar a leer").assertExists()
        compose.onNodeWithText("¿Querés sincronizar?").assertExists()
        compose.onNodeWithText("Crear cuenta").assertExists()
        compose.onNodeWithText("Iniciar sesión").assertExists()
    }

    // WEL-007, GST-018: logo y botones con nombre para TalkBack
    @Test
    fun logoAndButtonsHaveAccessibleNames() {
        compose.setContent { WelcomeScreen(onStartReading = {}, onCreateAccount = {}, onSignIn = {}) }
        compose.onNodeWithContentDescription("tuck.").assertExists()
        compose.onNodeWithTag("welcome-start").assertHasClickAction()
        compose.onNodeWithTag("welcome-create").assertHasClickAction()
        compose.onNodeWithTag("welcome-signin").assertHasClickAction()
    }

    // GST-002
    @Test
    fun buttonsLeadToReadingSignUpAndSignIn() {
        var start = 0
        var create = 0
        var signIn = 0
        compose.setContent { WelcomeScreen(onStartReading = { start++ }, onCreateAccount = { create++ }, onSignIn = { signIn++ }) }
        compose.onNodeWithTag("welcome-start").performClick()
        compose.onNodeWithTag("welcome-create").performClick()
        compose.onNodeWithTag("welcome-signin").performClick()
        compose.waitForIdle()
        assertEquals(listOf(1, 1, 1), listOf(start, create, signIn))
    }
}
