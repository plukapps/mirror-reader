package com.pluk.reader.home

import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pluk.reader.ui.navigation.MainDestination
import com.pluk.reader.ui.navigation.MarginBottomBar
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MarginBottomBarTest {
    @get:Rule
    val compose = createComposeRule()

    // HOM-005 y HOM-007: cuatro destinos sin texto visible, cada uno con su nombre para TalkBack
    @Test
    fun everyDestinationHasAnAccessibleName() {
        compose.setContent { MarginBottomBar(MainDestination.Home) {} }
        listOf("Inicio", "Buscar", "Estantes", "Perfil").forEach {
            compose.onNodeWithContentDescription(it).assertExists()
        }
    }

    // HOM-005: solo el destino actual queda seleccionado
    @Test
    fun onlyTheCurrentDestinationIsSelected() {
        compose.setContent { MarginBottomBar(MainDestination.Shelves) {} }
        compose.onNodeWithTag("nav-Shelves").assertIsSelected()
        compose.onNodeWithTag("nav-Home").assertIsNotSelected()
        compose.onNodeWithTag("nav-Search").assertIsNotSelected()
        compose.onNodeWithTag("nav-Profile").assertIsNotSelected()
    }

    // HOM-005: tocar un destino lo informa
    @Test
    fun tappingADestinationReportsIt() {
        var clicked: MainDestination? = null
        compose.setContent { MarginBottomBar(MainDestination.Home) { clicked = it } }
        compose.onNodeWithTag("nav-Search").performClick()
        assertEquals(MainDestination.Search, clicked)
    }
}
