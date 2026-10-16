package com.pluk.reader.onboarding

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pluk.reader.ui.onboarding.ShortAllSetContent
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** GST-021: "Ya estás adentro" del alta corta (B3). */
@RunWith(AndroidJUnit4::class)
class ShortAllSetScreenTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun showsTheBooksThatGoToTheAccountAndGoesToTheLibrary() {
        var library = 0
        compose.setContent { ShortAllSetContent(name = "Ana", bookCount = 6, onLibrary = { library++ }) }
        compose.onNodeWithText("Ya estás\nadentro,\nAna.").assertExists()
        compose.onNodeWithText("Tus 6 libros, con su progreso y sus notas, pasan a tu cuenta. Iniciá sesión en otro dispositivo para tenerlos ahí.").assertExists()
        compose.onNodeWithText("Volver a la biblioteca").performClick()
        compose.waitForIdle()
        assertEquals(1, library)
    }

    @Test
    fun withoutBooks() {
        compose.setContent { ShortAllSetContent(name = null, bookCount = 0, onLibrary = {}) }
        compose.onNodeWithText("Iniciá sesión en otro dispositivo para tener ahí tus libros.").assertExists()
    }
}
