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

    // ONB-001: el contenido del diseño O1
    @Test
    fun showsTheDesignContent() {
        compose.setContent { WelcomeScreen(onCreateAccount = {}, onSignIn = {}) }
        compose.onNodeWithTag("welcome-headline").assertExists()
        compose.onNodeWithText("Tus EPUB, en un lugar tranquilo. Sincronizados en todos tus dispositivos.").assertExists()
        compose.onNodeWithText("Crear cuenta").assertExists()
        compose.onNodeWithText("Ya tengo una cuenta").assertExists()
        compose.onNodeWithText("margin. 2026 ©").assertExists()
    }

    // WEL-007, ONB-022: logo y botones con nombre para TalkBack
    @Test
    fun logoAndButtonsHaveAccessibleNames() {
        compose.setContent { WelcomeScreen(onCreateAccount = {}, onSignIn = {}) }
        compose.onNodeWithContentDescription("margin.").assertExists()
        compose.onNodeWithTag("welcome-create").assertHasClickAction()
        compose.onNodeWithTag("welcome-signin").assertHasClickAction()
    }

    // ONB-002
    @Test
    fun buttonsLeadToSignUpAndSignIn() {
        var create = 0
        var signIn = 0
        compose.setContent { WelcomeScreen(onCreateAccount = { create++ }, onSignIn = { signIn++ }) }
        compose.onNodeWithTag("welcome-create").performClick()
        compose.onNodeWithTag("welcome-signin").performClick()
        compose.waitForIdle()
        assertEquals(1, create)
        assertEquals(1, signIn)
    }
}
