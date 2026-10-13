package com.pluk.reader.profile

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pluk.reader.ui.profile.ChangePasswordContent
import com.pluk.reader.ui.profile.ChangePasswordViewModel
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ChangePasswordScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private fun show(state: ChangePasswordViewModel.UiState) = compose.setContent {
        ChangePasswordContent(state, onBack = {}, onCurrent = {}, onNew = {}, onConfirm = {}, onToggleCurrent = {}, onToggleNew = {}, onSave = {})
    }

    // PRF-020: "Guardar" deshabilitado hasta que se cumplen las reglas
    @Test
    fun saveDisabledUntilValid() {
        show(ChangePasswordViewModel.UiState(current = "vieja", new = "Nueva-2026", confirm = "Nueva"))
        compose.onNodeWithTag("password-save").assertIsNotEnabled()
        compose.onNodeWithText("Las contraseñas no coinciden.").assertExists()
    }

    // PRF-020
    @Test
    fun saveEnabledWhenValid() {
        show(ChangePasswordViewModel.UiState(current = "vieja", new = "Nueva-2026", confirm = "Nueva-2026"))
        compose.onNodeWithTag("password-save").assertIsEnabled()
    }

    // PRF-020: la actual incorrecta se dice bajo ese campo
    @Test
    fun wrongCurrentIsShown() {
        show(ChangePasswordViewModel.UiState(current = "vieja", currentWrong = true))
        compose.onNodeWithText("La contraseña actual no es correcta.").assertExists()
    }
}
