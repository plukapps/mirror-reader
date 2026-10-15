package com.pluk.reader.profile

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.compose.ui.test.onNodeWithTag
import com.pluk.reader.domain.appearance.Appearance
import com.pluk.reader.domain.help.FaqId
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
        // PRF-007: la sección de dispositivos está oculta por ahora
        compose.onNodeWithText("Dispositivos vinculados").assertDoesNotExist()
        compose.onNodeWithText("Pixel 8 · este dispositivo").assertDoesNotExist()
        compose.onNodeWithText("tuck. 1.0").performScrollTo().assertExists()
    }

    // PRF-008: sin "Cambiar contraseña" para cuentas de Google
    @Test
    fun hidesChangePasswordWhenNotAvailable() {
        show(state.copy(canChangePassword = false))
        compose.onNodeWithText("Cambiar contraseña").assertDoesNotExist()
    }

    // PRF-008, PRF-020: "Cambiar contraseña" abre su pantalla, no el aviso
    @Test
    fun changePasswordOpensItsScreen() {
        var opened = 0
        var notices = 0
        show(actions = ProfileActions(onComingSoon = { notices++ }, onChangePassword = { opened++ }))
        compose.onNodeWithText("Cambiar contraseña").performScrollTo().performClick()
        assertEquals(1, opened)
        assertEquals(0, notices)
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
        compose.onNodeWithText("Eliminar cuenta").performScrollTo().performClick()
        compose.onNodeWithText("Descargar libros automáticamente").performScrollTo().performClick()
        assertEquals(2, notices)
    }

    // PRF-006, PLN-012: tocar el bloque del espacio abre Mi plan
    @Test
    fun storageOpensMyPlan() {
        var opened = 0
        show(actions = ProfileActions(onOpenPlan = { opened++ }))
        compose.onNodeWithText("1,2 GB de 2 GB usados").performClick()
        assertEquals(1, opened)
    }

    // PRF-006, PLN-002: "Mejorar plan" abre Planes, no el aviso
    @Test
    fun upgradeOpensPlans() {
        var opened = 0
        var notices = 0
        show(actions = ProfileActions(onComingSoon = { notices++ }, onUpgrade = { opened++ }))
        compose.onNodeWithText("Mejorar plan").performClick()
        assertEquals(1, opened)
        assertEquals(0, notices)
    }

    // PRF-023: "Sincronizar solo con Wi-Fi" muestra su estado y tocarlo lo cambia, sin aviso
    @Test
    fun wifiOnlySwitchShowsStateAndToggles() {
        var chosen: Boolean? = null
        var notices = 0
        show(state.copy(wifiOnly = true), ProfileActions(onComingSoon = { notices++ }, onWifiOnly = { chosen = it }))
        compose.onNodeWithText("Sincronizar solo con Wi-Fi", substring = true).performScrollTo()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "activado"))
            .performClick()
        assertEquals(false, chosen)
        assertEquals(0, notices)
    }

    // PRF-021: la apariencia muestra la elegida (por defecto, Sistema) y tocar otra la informa
    @Test
    fun appearanceShowsAndReportsChoice() {
        var chosen: Appearance? = null
        show(actions = ProfileActions(onAppearance = { chosen = it }))
        compose.onNodeWithText("Sistema").performScrollTo().assertIsSelected()
        compose.onNodeWithText("Oscuro").performClick()
        assertEquals(Appearance.DARK, chosen)
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

    // PRF-017: tocar una pregunta o "Todas las preguntas" las abre
    @Test
    fun helpRowsOpenQuestions() {
        var opened: FaqId? = null
        var all = false
        show(actions = ProfileActions(onFaq = { opened = it }, onAllFaq = { all = true }))
        compose.onNodeWithText("¿Cuánto espacio tengo?").performScrollTo().performClick()
        compose.onNodeWithText("Todas las preguntas").performScrollTo().performClick()
        assertEquals(FaqId.SPACE, opened)
        assertEquals(true, all)
    }

    // PLN-015, PRF-014: en Free, Sepia y Noche llevan la marca "Plus"; con un plan pago, no
    @Test
    fun paidThemesAreMarkedOnFree() {
        show(state.copy(readingOptionsLocked = true))
        compose.onNodeWithContentDescription("Sepia, Incluido en Plus").performScrollTo().assertExists()
        compose.onNodeWithContentDescription("Noche, Incluido en Plus").assertExists()
        compose.onNodeWithContentDescription("Clásico").assertExists()
    }

    @Test
    fun paidThemesAreNotMarkedOnPaidPlans() {
        show(state.copy(readingOptionsLocked = false))
        compose.onNodeWithContentDescription("Sepia").performScrollTo().assertExists()
    }

    // GST-009, GST-018 (09g): sin cuenta, la tarjeta invita a crearla y "Cuenta" tiene solo "Ya tengo una cuenta"
    @Test
    fun withoutAccountShowsTheInvitationAndHidesAccountRows() {
        var create = 0
        var signIn = 0
        show(
            ProfileUiState(signedIn = false, deviceName = "Pixel 8"),
            ProfileActions(onCreateAccount = { create++ }, onSignIn = { signIn++ }),
        )
        compose.onNodeWithContentDescription("Sin cuenta. Crear una cuenta").assertExists().performClick()
        compose.onNodeWithText("Ya tengo una cuenta").performClick()
        compose.waitForIdle()
        assertEquals(listOf(1, 1), listOf(create, signIn))
        compose.onNodeWithText("Cerrar sesión").assertDoesNotExist()
        compose.onNodeWithText("Eliminar cuenta").assertDoesNotExist()
        compose.onNodeWithText("Cambiar contraseña").assertDoesNotExist()
        compose.onNodeWithText("Sincronizar solo con Wi-Fi").assertDoesNotExist()
        compose.onNodeWithTag("profile-storage").assertDoesNotExist()
    }
}
