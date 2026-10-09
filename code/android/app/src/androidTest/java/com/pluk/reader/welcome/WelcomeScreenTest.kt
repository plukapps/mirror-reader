package com.pluk.reader.welcome

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
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

    // WEL-004: el contenido del diseño 01
    @Test
    fun showsTheDesignContent() {
        compose.setContent { WelcomeScreen(onStart = {}) }
        compose.onNodeWithTag("welcome-headline").assertExists()
        compose.onNodeWithText("Tus EPUB, en un lugar tranquilo, en todos tus dispositivos.").assertExists()
        compose.onNodeWithText("margin. 2026 ©").assertExists()
    }

    // WEL-007: logo y botón con nombre para TalkBack
    @Test
    fun logoAndButtonHaveAccessibleNames() {
        compose.setContent { WelcomeScreen(onStart = {}) }
        compose.onNodeWithContentDescription("margin.").assertExists()
        compose.onNodeWithContentDescription("Comenzar").assertHasClickAction()
    }

    // WEL-005: "Comenzar" (por TalkBack o tocando) llama a la acción
    @Test
    fun startCallsTheAction() {
        var started = 0
        compose.setContent { WelcomeScreen(onStart = { started++ }) }
        compose.onNodeWithContentDescription("Comenzar").performSemanticsAction(SemanticsActions.OnClick)
        compose.onNodeWithTag("welcome-start").performClick()
        compose.waitForIdle()
        assertEquals(2, started)
    }
}
