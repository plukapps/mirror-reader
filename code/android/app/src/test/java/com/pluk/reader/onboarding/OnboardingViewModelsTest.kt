package com.pluk.reader.onboarding

import androidx.lifecycle.SavedStateHandle
import com.pluk.reader.domain.account.AccountUser
import com.pluk.reader.domain.account.AuthError
import com.pluk.reader.domain.onboarding.DailyReminder
import com.pluk.reader.domain.onboarding.Genre
import com.pluk.reader.domain.onboarding.ReadingGoal
import com.pluk.reader.ui.onboarding.AllSetViewModel
import com.pluk.reader.ui.onboarding.ForgotPasswordViewModel
import com.pluk.reader.ui.onboarding.GoalViewModel
import com.pluk.reader.ui.onboarding.InterestsViewModel
import com.pluk.reader.ui.onboarding.ResetPasswordViewModel
import com.pluk.reader.ui.onboarding.SignInViewModel
import com.pluk.reader.ui.onboarding.SignUpViewModel
import com.pluk.reader.ui.onboarding.VerifyEmailViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
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

/** ViewModels del onboarding: ONB-004 a ONB-011, ONB-013 a ONB-018, ONB-020, ONB-021. */
@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingViewModelsTest {
    private val dispatcher = StandardTestDispatcher()
    private val auth = FakeAuth()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun SignUpViewModel.fillValid() {
        onName("Ana Ruiz")
        onEmail("ana@mail.com")
        onPassword("slow-reads-2026")
        onTermsAccepted(true)
    }

    // ONB-004: cada condición habilita el botón
    @Test
    fun createAccountNeedsNameEmailValidPasswordAndTerms() {
        val vm = SignUpViewModel(auth, SavedStateHandle())
        vm.fillValid()
        assertTrue(vm.state.value.canSubmit)

        vm.onTermsAccepted(false)
        assertFalse(vm.state.value.canSubmit)
        vm.onTermsAccepted(true)
        vm.onPassword("abcdefg1")
        assertFalse(vm.state.value.canSubmit)
        vm.onPassword("slow-reads-2026")
        vm.onEmail("ana@mail")
        assertFalse(vm.state.value.canSubmit)
        vm.onEmail("ana@mail.com")
        vm.onName(" ")
        assertFalse(vm.state.value.canSubmit)
    }

    // ONB-006, ONB-021
    @Test
    fun creatingTheAccountGoesToVerifyAndForgetsThePassword() = runTest(dispatcher) {
        val vm = SignUpViewModel(auth, SavedStateHandle())
        vm.fillValid()
        vm.submit()
        assertTrue(vm.state.value.busy)
        assertFalse(vm.state.value.canSubmit)
        advanceUntilIdle()

        assertEquals(listOf(Triple("Ana Ruiz", "ana@mail.com", "slow-reads-2026")), auth.created)
        assertEquals(SignUpViewModel.Next.VerifyEmail, vm.state.value.next)
        assertEquals("", vm.state.value.password)
    }

    // ONB-006
    @Test
    fun emailInUseIsReported() = runTest(dispatcher) {
        auth.createResult = authFailure(AuthError.EmailInUse)
        val vm = SignUpViewModel(auth, SavedStateHandle())
        vm.fillValid()
        vm.submit()
        advanceUntilIdle()

        assertEquals(AuthError.EmailInUse, vm.state.value.error)
        assertNull(vm.state.value.next)
        assertFalse(vm.state.value.busy)
    }

    // ONB-006: Google salta la verificación
    @Test
    fun googleSignUpGoesToInterests() = runTest(dispatcher) {
        val vm = SignUpViewModel(auth, SavedStateHandle())
        vm.onGoogleToken("token")
        advanceUntilIdle()
        assertEquals(SignUpViewModel.Next.Interests, vm.state.value.next)
    }

    // ONB-008: los datos vuelven sin contraseña
    @Test
    fun signUpStartsWithTheDataBroughtBackFromChange() {
        val vm = SignUpViewModel(auth, SavedStateHandle(mapOf("name" to "Ana", "email" to "ana@mail.com")))
        assertEquals("Ana", vm.state.value.name)
        assertEquals("ana@mail.com", vm.state.value.email)
        assertEquals("", vm.state.value.password)
    }

    // ONB-007: detecta la verificación sola mientras se ve
    @Test
    fun verificationIsDetectedWhileVisible() = runTest(dispatcher) {
        val vm = VerifyEmailViewModel(auth, auth, SavedStateHandle())
        vm.onVisible()
        runCurrent()
        assertEquals(1, auth.verificationChecks)
        assertNull(vm.state.value.next)

        auth.verified = Result.success(true)
        advanceTimeBy(VerifyEmailViewModel.POLL_MS + 1)
        assertEquals(VerifyEmailViewModel.Next.Interests, vm.state.value.next)

        vm.onHidden()
    }

    // ONB-007: oculta, no consulta
    @Test
    fun hiddenScreenStopsPolling() = runTest(dispatcher) {
        val vm = VerifyEmailViewModel(auth, auth, SavedStateHandle())
        vm.onVisible()
        runCurrent()
        vm.onHidden()
        advanceTimeBy(VerifyEmailViewModel.POLL_MS * 5)
        assertEquals(1, auth.verificationChecks)
    }

    // ONB-007: reenviar espera 60 s
    @Test
    fun resendWaitsSixtySecondsAfterTheEmailWasSent() = runTest(dispatcher) {
        auth.user.value = AccountUser("u1", "ana@mail.com", "Ana", needsEmailVerification = true)
        val vm = VerifyEmailViewModel(auth, auth, SavedStateHandle(mapOf("justSent" to true)))
        runCurrent()
        assertEquals("ana@mail.com", vm.state.value.email)
        assertEquals(60, vm.state.value.resendSeconds)
        assertFalse(vm.state.value.canResend)

        vm.resend()
        assertEquals(0, auth.verificationsSent)

        advanceTimeBy(60_001)
        assertTrue(vm.state.value.canResend)
        vm.resend()
        runCurrent()
        assertEquals(1, auth.verificationsSent)
        assertTrue(vm.state.value.resent)
        assertEquals(60, vm.state.value.resendSeconds)
        advanceUntilIdle()
    }

    // ONB-008
    @Test
    fun changeDiscardsTheUnverifiedAccountAndReturnsTheData() = runTest(dispatcher) {
        auth.user.value = AccountUser("u1", "ana@mail.com", "Ana Ruiz", needsEmailVerification = true)
        val vm = VerifyEmailViewModel(auth, auth, SavedStateHandle())
        runCurrent()
        vm.change()
        advanceUntilIdle()

        assertEquals(1, auth.discarded)
        assertEquals(VerifyEmailViewModel.Next.Change("Ana Ruiz", "ana@mail.com"), vm.state.value.next)
    }

    // ONB-008: ya verificada, no se borra
    @Test
    fun changeOnAVerifiedAccountGoesOn() = runTest(dispatcher) {
        auth.verified = Result.success(true)
        val vm = VerifyEmailViewModel(auth, auth, SavedStateHandle())
        vm.change()
        advanceUntilIdle()

        assertEquals(0, auth.discarded)
        assertEquals(VerifyEmailViewModel.Next.Interests, vm.state.value.next)
    }

    // ONB-009
    @Test
    fun interestsNeedThreeAndAreSaved() = runTest(dispatcher) {
        val prefs = FakePreferences()
        val vm = InterestsViewModel(prefs)
        vm.toggle(Genre.Poetry)
        vm.toggle(Genre.Essays)
        vm.continueOn()
        advanceUntilIdle()
        assertFalse(vm.state.value.done)

        vm.toggle(Genre.Drama)
        vm.toggle(Genre.Poetry)
        vm.toggle(Genre.Poetry) // marcar y desmarcar
        vm.continueOn()
        advanceUntilIdle()

        assertEquals(setOf(Genre.Poetry, Genre.Essays, Genre.Drama), prefs.interests.value)
        assertTrue(vm.state.value.done)
    }

    // ONB-009
    @Test
    fun skippingInterestsSavesNothing() = runTest(dispatcher) {
        val prefs = FakePreferences()
        val vm = InterestsViewModel(prefs)
        vm.toggle(Genre.Poetry)
        vm.skip()
        advanceUntilIdle()
        assertTrue(vm.state.value.done)
        assertEquals(emptySet<Genre>(), prefs.interests.value)
    }

    // ONB-010, ONB-012
    @Test
    fun finishingSavesTheGoalAndSchedulesTheReminder() = runTest(dispatcher) {
        val prefs = FakePreferences()
        val scheduler = FakeScheduler()
        val vm = GoalViewModel(prefs, scheduler)
        assertEquals(ReadingGoal.Regular, vm.state.value.goal)
        assertEquals(DailyReminder(true, 21, 30), vm.state.value.reminder)

        vm.selectGoal(ReadingGoal.Serious)
        vm.setReminderTime(22, 0)
        vm.finish()
        advanceUntilIdle()

        assertEquals(ReadingGoal.Serious, prefs.goal.value)
        assertEquals(DailyReminder(true, 22, 0), prefs.reminder.value)
        assertEquals(DailyReminder(true, 22, 0) to ReadingGoal.Serious, scheduler.scheduled)
        assertTrue(vm.state.value.done)
    }

    // ONB-011: sin permiso se guarda con el recordatorio apagado
    @Test
    fun withoutNotificationPermissionTheReminderIsSavedOff() = runTest(dispatcher) {
        val prefs = FakePreferences()
        val scheduler = FakeScheduler(notificationsAllowed = false)
        val vm = GoalViewModel(prefs, scheduler)
        assertTrue(vm.needsNotificationPermission)

        vm.finish()
        advanceUntilIdle()

        assertEquals(DailyReminder(false, 21, 30), prefs.reminder.value)
        assertNull(scheduler.scheduled)
        assertEquals(1, scheduler.cancelled)
    }

    // ONB-010
    @Test
    fun skippingTheGoalSavesNothing() = runTest(dispatcher) {
        val prefs = FakePreferences()
        val scheduler = FakeScheduler()
        val vm = GoalViewModel(prefs, scheduler)
        vm.skip()
        advanceUntilIdle()
        assertTrue(vm.state.value.done)
        assertNull(prefs.goal.value)
        assertNull(scheduler.scheduled)
    }

    // ONB-013
    @Test
    fun allSetGreetsByFirstNameWithTheChosenGoal() = runTest(dispatcher) {
        val prefs = FakePreferences()
        prefs.saveGoal(ReadingGoal.Regular, DailyReminder(true, 21, 30))
        auth.user.value = AccountUser("u1", "ana@mail.com", "Ana Ruiz")
        val vm = AllSetViewModel(auth, prefs)
        backgroundScope.launch { vm.state.collect() }
        advanceUntilIdle()

        assertEquals(AllSetViewModel.UiState("Ana", ReadingGoal.Regular, DailyReminder(true, 21, 30)), vm.state.value)
    }

    // ONB-016
    @Test
    fun signInGoesHomeOrToVerification() = runTest(dispatcher) {
        val vm = SignInViewModel(auth)
        vm.onEmail("ana@mail.com")
        vm.onPassword("x")
        vm.submit()
        advanceUntilIdle()
        assertEquals(SignInViewModel.Next.Home, vm.state.value.next)

        auth.signInResult = Result.success(AccountUser("u1", "ana@mail.com", needsEmailVerification = true))
        vm.onNavigated()
        vm.onPassword("x")
        vm.submit()
        advanceUntilIdle()
        assertEquals(SignInViewModel.Next.VerifyEmail, vm.state.value.next)
    }

    // ONB-016: al llegar a verificar desde el ingreso, el email sale en ese momento
    @Test
    fun signingInUnverifiedSendsTheVerificationEmail() = runTest(dispatcher) {
        auth.signInResult = Result.success(AccountUser("u1", "ana@mail.com", needsEmailVerification = true))
        val vm = SignInViewModel(auth)
        vm.onEmail("ana@mail.com")
        vm.onPassword("x")
        vm.submit()
        advanceUntilIdle()

        assertEquals(1, auth.verificationsSent)
        assertEquals(SignInViewModel.Next.VerifyEmail, vm.state.value.next)
    }

    // ONB-016: una cuenta verificada no recibe email
    @Test
    fun signingInVerifiedSendsNothing() = runTest(dispatcher) {
        val vm = SignInViewModel(auth)
        vm.onEmail("ana@mail.com")
        vm.onPassword("x")
        vm.submit()
        advanceUntilIdle()
        assertEquals(0, auth.verificationsSent)
    }

    // ONB-015: el error se muestra y se borra al escribir
    @Test
    fun signInErrorIsShownUntilTheUserTypes() = runTest(dispatcher) {
        auth.signInResult = authFailure(AuthError.InvalidCredentials)
        val vm = SignInViewModel(auth)
        vm.onEmail("ana@mail.com")
        vm.onPassword("wrong")
        vm.submit()
        advanceUntilIdle()
        assertEquals(AuthError.InvalidCredentials, vm.state.value.error)
        assertNull(vm.state.value.next)

        vm.onPassword("wrong2")
        assertNull(vm.state.value.error)
    }

    // ONB-020
    @Test
    fun googleFailureIsShown() {
        val vm = SignInViewModel(auth)
        vm.onGoogleFailed()
        assertEquals(AuthError.Unknown, vm.state.value.error)
    }

    // ONB-017
    @Test
    fun forgotPasswordSendsTheLinkAndWaitsThirtySeconds() = runTest(dispatcher) {
        val vm = ForgotPasswordViewModel(auth, SavedStateHandle(mapOf("email" to "ana@mail.com")))
        assertTrue(vm.state.value.canSend)
        vm.send()
        runCurrent()

        assertEquals(listOf("ana@mail.com"), auth.resetsSent)
        assertTrue(vm.state.value.sent)
        assertEquals(30, vm.state.value.resendSeconds)
        assertFalse(vm.state.value.canSend)

        advanceTimeBy(30_001)
        assertTrue(vm.state.value.canSend)
    }

    // ONB-018
    @Test
    fun newPasswordNeedsValidAndMatchingAndSignsIn() = runTest(dispatcher) {
        val vm = ResetPasswordViewModel(auth, SavedStateHandle(mapOf("oobCode" to "code")))
        advanceUntilIdle()
        assertEquals("ana@mail.com", vm.state.value.email)

        vm.onPassword("slow-reads-2026")
        vm.onConfirm("slow-reads-202")
        assertTrue(vm.state.value.mismatch)
        assertFalse(vm.state.value.canSave)

        vm.onConfirm("slow-reads-2026")
        assertTrue(vm.state.value.canSave)
        vm.save()
        advanceUntilIdle()

        assertEquals(listOf("code" to "slow-reads-2026"), auth.confirmed)
        assertEquals(listOf("ana@mail.com" to "slow-reads-2026"), auth.signIns)
        assertEquals(ResetPasswordViewModel.Next.Home, vm.state.value.next)
    }

    // ONB-018: enlace vencido o usado
    @Test
    fun expiredLinkIsReported() = runTest(dispatcher) {
        auth.resetEmail = authFailure(AuthError.InvalidLink)
        val vm = ResetPasswordViewModel(auth, SavedStateHandle(mapOf("oobCode" to "old")))
        advanceUntilIdle()
        assertTrue(vm.state.value.linkInvalid)
        assertFalse(vm.state.value.loading)
    }
}
