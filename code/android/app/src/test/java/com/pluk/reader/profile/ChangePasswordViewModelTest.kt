package com.pluk.reader.profile

import com.pluk.reader.domain.account.AuthError
import com.pluk.reader.domain.onboarding.canSaveNewPassword
import com.pluk.reader.onboarding.FakeAuth
import com.pluk.reader.onboarding.authFailure
import com.pluk.reader.ui.profile.ChangePasswordViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Cambiar contraseña desde Perfil (PRF-020). */
@OptIn(ExperimentalCoroutinesApi::class)
class ChangePasswordViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val auth = FakeAuth()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    // PRF-020: actual no vacía, nueva válida (ONB-005), distinta de la actual e igual a la confirmación.
    @Test
    fun saveNeedsCurrentValidDifferentAndConfirmedPassword() {
        assertTrue(canSaveNewPassword("vieja", "Nueva-2026", "Nueva-2026"))
        assertFalse(canSaveNewPassword("", "Nueva-2026", "Nueva-2026")) // sin la actual
        assertFalse(canSaveNewPassword("vieja", "nuevaaaa", "nuevaaaa")) // no cumple ONB-005
        assertFalse(canSaveNewPassword("vieja", "Nueva-2026", "Nueva-202")) // no coincide
        assertFalse(canSaveNewPassword("Nueva-2026", "Nueva-2026", "Nueva-2026")) // igual a la actual
    }

    // PRF-020: "Guardar" sigue la regla a medida que se escribe.
    @Test
    fun saveEnablesAsFieldsAreFilled() {
        val vm = ChangePasswordViewModel(auth)
        assertFalse(vm.state.value.canSave)
        vm.onCurrent("vieja")
        vm.onNew("Nueva-2026")
        assertFalse(vm.state.value.canSave)
        vm.onConfirm("Nueva-202")
        assertTrue(vm.state.value.mismatch)
        assertFalse(vm.state.value.canSave)
        vm.onConfirm("Nueva-2026")
        assertFalse(vm.state.value.mismatch)
        assertTrue(vm.state.value.canSave)
    }

    // PRF-020: al guardar bien, se pide el cambio con la actual y la nueva, y la pantalla vuelve a Perfil.
    @Test
    fun savingChangesThePasswordAndFinishes() = runTest(dispatcher) {
        val vm = filled()
        vm.save()
        advanceUntilIdle()
        assertEquals(listOf("vieja" to "Nueva-2026"), auth.passwordChanges)
        assertTrue(vm.state.value.done)
        assertFalse(vm.state.value.busy)
    }

    // PRF-020, ONB-021: mientras espera al servidor muestra que trabaja y no acepta otro toque.
    @Test
    fun busyWhileWaitingAndIgnoresSecondTap() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>()
        val slow = object : FakeAuth() {
            override suspend fun changePassword(current: String, newPassword: String): Result<Unit> {
                passwordChanges += current to newPassword
                gate.await()
                return Result.success(Unit)
            }
        }
        val vm = filled(slow)
        vm.save()
        runCurrent()
        assertTrue(vm.state.value.busy)
        assertFalse(vm.state.value.canSave)
        vm.save()
        gate.complete(Unit)
        advanceUntilIdle()
        assertEquals(1, slow.passwordChanges.size)
        assertTrue(vm.state.value.done)
    }

    // PRF-020: con la actual incorrecta, el error va bajo ese campo y no termina.
    @Test
    fun wrongCurrentPasswordIsShownOnThatField() = runTest(dispatcher) {
        auth.changePasswordResult = authFailure(AuthError.InvalidCredentials)
        val vm = filled()
        vm.save()
        advanceUntilIdle()
        assertTrue(vm.state.value.currentWrong)
        assertNull(vm.state.value.error)
        assertFalse(vm.state.value.done)
        // Al corregir la actual, el error se va.
        vm.onCurrent("vieja2")
        assertFalse(vm.state.value.currentWrong)
    }

    // PRF-020, ONB-015: sin conexión y demasiados intentos se avisan como en el onboarding.
    @Test
    fun networkAndTooManyRequestsAreGeneralErrors() = runTest(dispatcher) {
        auth.changePasswordResult = authFailure(AuthError.Network)
        val vm = filled()
        vm.save()
        advanceUntilIdle()
        assertEquals(AuthError.Network, vm.state.value.error)
        assertFalse(vm.state.value.currentWrong)
        assertFalse(vm.state.value.done)

        auth.changePasswordResult = authFailure(AuthError.TooManyRequests)
        vm.save()
        advanceUntilIdle()
        assertEquals(AuthError.TooManyRequests, vm.state.value.error)
    }

    private fun filled(repo: FakeAuth = auth) = ChangePasswordViewModel(repo).apply {
        onCurrent("vieja")
        onNew("Nueva-2026")
        onConfirm("Nueva-2026")
    }
}
