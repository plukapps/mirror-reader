package com.pluk.reader.profile

import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.compose.ui.test.onNodeWithTag
import com.pluk.reader.domain.model.LineSpacing
import com.pluk.reader.domain.model.ReaderSettings
import com.pluk.reader.domain.model.ReadingTheme
import com.pluk.reader.ui.profile.ProfileActions
import com.pluk.reader.ui.profile.ProfileContent
import com.pluk.reader.ui.profile.ProfileUiState
import com.pluk.reader.ui.profile.StorageUi
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProfileScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val state = ProfileUiState(
        name = "Ana Ruiz",
        email = "ana.ruiz@gmail.com",
        initial = "A",
        canChangePassword = true,
        storage = StorageUi("1,2 GB", "2 GB", "0,8 GB", 0.6f, isFreePlan = true),
        deviceName = "Pixel 8",
    )

    private fun show(state: ProfileUiState = this.state, actions: ProfileActions = ProfileActions()) =
        compose.setContent { ProfileContent(state, actions, version = "1.0", isTablet = false) }

    // PRF-005, PRF-006, PRF-007, PRF-019: cabecera, espacio, dispositivo y pie
    @Test
    fun showsAccountStorageDeviceAndVersion() {
        show()
        compose.onNodeWithContentDescription("Cuenta de Ana Ruiz, ana.ruiz@gmail.com").assertExists()
        compose.onNodeWithText("GRATIS").assertExists()
        compose.onNodeWithText("1,2 GB de 2 GB usados").assertExists()
        compose.onNodeWithText("0,8 GB libres").assertExists()
        compose.onNodeWithText("Pixel 8 · este dispositivo").assertExists()
        compose.onNodeWithText("margin. 1.0").performScrollTo().assertExists()
    }

    // PRF-008: sin "Cambiar contraseña" para cuentas de Google
    @Test
    fun hidesChangePasswordWhenNotAvailable() {
        show(state.copy(canChangePassword = false))
        compose.onNodeWithText("Cambiar contraseña").assertDoesNotExist()
    }

    // PRF-006: sin espacio leído ni guardado, se avisa
    @Test
    fun reportsStorageFailure() {
        show(state.copy(storage = null, storageFailed = true))
        compose.onNodeWithText("No se pudo cargar el espacio").assertExists()
        compose.onNodeWithText("Mejorar plan").assertDoesNotExist()
    }

    // PRF-003: lo que todavía no existe avisa al tocarlo
    @Test
    fun pendingFeaturesShowComingSoon() {
        var notices = 0
        show(actions = ProfileActions(onComingSoon = { notices++ }))
        compose.onNodeWithText("Mejorar plan").performClick()
        compose.onNodeWithText("Eliminar cuenta").performScrollTo().performClick()
        compose.onNodeWithText("Descargar libros automáticamente").performScrollTo().performClick()
        assertEquals(3, notices)
    }

    // PRF-016: la apariencia muestra el comportamiento de hoy (claro)
    @Test
    fun appearanceShowsLight() {
        show()
        compose.onNodeWithText("Claro").performScrollTo().assertIsSelected()
    }

    // PRF-013: la fila "Letra" muestra tipo y tamaño y abre el panel
    @Test
    fun fontRowShowsValueAndOpensSheet() {
        show(state.copy(reading = ReaderSettings(fontScale = 1.1)))
        compose.onNodeWithText("Newsreader · 20").performScrollTo().performClick()
        compose.onNodeWithTag("profile-font-sheet").assertExists()
    }

    // PRF-014, PRF-015: tocar un tema o un interlineado lo elige
    @Test
    fun themeAndSpacingReportChoice() {
        var theme: ReadingTheme? = null
        var spacing: LineSpacing? = null
        show(actions = ProfileActions(onTheme = { theme = it }, onLineSpacing = { spacing = it }))
        compose.onNodeWithTag("profile-theme-SEPIA").performScrollTo().performClick()
        compose.onNodeWithTag("profile-spacing-WIDE").performScrollTo().performClick()
        assertEquals(ReadingTheme.SEPIA, theme)
        assertEquals(LineSpacing.WIDE, spacing)
    }
}
