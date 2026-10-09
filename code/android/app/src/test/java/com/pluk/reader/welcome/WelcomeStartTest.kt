package com.pluk.reader.welcome

import com.pluk.reader.domain.account.AccountRepository
import com.pluk.reader.domain.account.AccountUser
import com.pluk.reader.domain.model.ImportOutcome
import com.pluk.reader.domain.model.LibraryBook
import com.pluk.reader.domain.repository.LibraryRepository
import com.pluk.reader.domain.repository.WelcomeRepository
import com.pluk.reader.domain.showsWelcome
import com.pluk.reader.domain.usecase.LibrarySync
import com.pluk.reader.remote.FakePositionBackend
import com.pluk.reader.remote.FakeSyncBackend
import com.pluk.reader.ui.MainViewModel
import com.pluk.reader.ui.MainViewModel.Start
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** WEL-003, WEL-005, WEL-006: qué pantalla abre la app y cuándo deja de mostrarse la bienvenida. */
@OptIn(ExperimentalCoroutinesApi::class)
class WelcomeStartTest {
    private val dispatcher = StandardTestDispatcher()

    private class FakeWelcome(completed: Boolean) : WelcomeRepository {
        val stored = MutableStateFlow(completed)
        override val completed: Flow<Boolean> = stored
        override suspend fun markCompleted() { stored.value = true }
    }

    private class FakeAccount(user: Flow<AccountUser?>) : AccountRepository {
        override val user = user
        override suspend fun signIn(email: String, password: String) = Result.failure<AccountUser>(IllegalStateException())
    }

    private object NoLibrary : LibraryRepository {
        override val books: Flow<List<LibraryBook>> = flowOf(emptyList())
        override suspend fun import(uri: String): ImportOutcome = ImportOutcome.Rejected("x")
    }

    private val someone = AccountUser("u1", null)

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(welcome: WelcomeRepository, user: Flow<AccountUser?>) =
        MainViewModel(
            NoLibrary,
            LibrarySync(FakeSyncBackend().useCase(), FakeAccount(user), CoroutineScope(dispatcher)),
            FakePositionBackend().sync(FakeAccount(user), CoroutineScope(dispatcher)),
            welcome,
            FakeAccount(user),
        )

    // WEL-003: la regla
    @Test
    fun welcomeShowsUntilCompletedAndWheneverThereIsNoSession() {
        assertTrue(showsWelcome(completed = false, signedIn = false))
        assertTrue(showsWelcome(completed = false, signedIn = true))
        assertTrue(showsWelcome(completed = true, signedIn = false))
        assertFalse(showsWelcome(completed = true, signedIn = true))
    }

    // WEL-003: primera vez
    @Test
    fun firstLaunchOpensTheWelcome() = runTest(dispatcher) {
        val vm = viewModel(FakeWelcome(completed = false), flowOf(someone))
        advanceUntilIdle()
        assertEquals(Start.Welcome, vm.start.value)
    }

    // WEL-003: completada, pero sin sesión
    @Test
    fun withoutSessionTheWelcomeShowsAgain() = runTest(dispatcher) {
        val vm = viewModel(FakeWelcome(completed = true), flowOf(null))
        advanceUntilIdle()
        assertEquals(Start.Welcome, vm.start.value)
    }

    // WEL-003: completada y con sesión
    @Test
    fun completedWithSessionOpensHome() = runTest(dispatcher) {
        val vm = viewModel(FakeWelcome(completed = true), flowOf(someone))
        advanceUntilIdle()
        assertEquals(Start.Home, vm.start.value)
    }

    // WEL-006: mientras no se sabe la sesión, ni bienvenida ni Inicio
    @Test
    fun staysLoadingUntilTheSessionIsKnown() = runTest(dispatcher) {
        val session = CompletableDeferred<AccountUser?>()
        val vm = viewModel(FakeWelcome(completed = true), flow { emit(session.await()) })
        advanceUntilIdle()
        assertEquals(Start.Loading, vm.start.value)

        session.complete(someone)
        advanceUntilIdle()
        assertEquals(Start.Home, vm.start.value)
    }

    // WEL-003: se decide una vez por arranque; perder la sesión con la app abierta no reubica
    @Test
    fun theDecisionIsTakenOncePerLaunch() = runTest(dispatcher) {
        val user = MutableStateFlow<AccountUser?>(someone)
        val vm = viewModel(FakeWelcome(completed = true), user)
        advanceUntilIdle()

        user.value = null
        advanceUntilIdle()

        assertEquals(Start.Home, vm.start.value)
    }

    // WEL-005: "Comenzar" registra la bienvenida como completada
    @Test
    fun completingTheWelcomeIsRemembered() = runTest(dispatcher) {
        val welcome = FakeWelcome(completed = false)
        val vm = viewModel(welcome, flowOf(someone))

        vm.completeWelcome()
        advanceUntilIdle()

        assertTrue(welcome.stored.value)
    }
}
