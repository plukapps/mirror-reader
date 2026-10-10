package com.pluk.reader.onboarding

import com.pluk.reader.domain.account.AccountRepository
import com.pluk.reader.domain.account.AccountUser
import com.pluk.reader.domain.model.ImportOutcome
import com.pluk.reader.domain.model.LibraryBook
import com.pluk.reader.domain.repository.LibraryRepository
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
import org.junit.Before
import org.junit.Test

/** ONB-019, WEL-006: qué pantalla abre la app. */
@OptIn(ExperimentalCoroutinesApi::class)
class StartTest {
    private val dispatcher = StandardTestDispatcher()

    private class FakeAccount(user: Flow<AccountUser?>) : AccountRepository {
        override val user = user
        override suspend fun signIn(email: String, password: String) = Result.failure<AccountUser>(IllegalStateException())
    }

    private object NoLibrary : LibraryRepository {
        override val books: Flow<List<LibraryBook>> = flowOf(emptyList())
        override suspend fun import(uri: String): ImportOutcome = ImportOutcome.Rejected("x")
    }

    private val verified = AccountUser("u1", "a@b.c")
    private val unverified = AccountUser("u1", "a@b.c", needsEmailVerification = true)

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(user: Flow<AccountUser?>) =
        MainViewModel(
            NoLibrary,
            LibrarySync(FakeSyncBackend().useCase(), FakeAccount(user), CoroutineScope(dispatcher)),
            FakePositionBackend().sync(FakeAccount(user), CoroutineScope(dispatcher)),
            FakeAccount(user),
        )

    @Test
    fun withoutSessionOpensTheWelcome() = runTest(dispatcher) {
        val vm = viewModel(flowOf(null))
        advanceUntilIdle()
        assertEquals(Start.Welcome, vm.start.value)
    }

    @Test
    fun unverifiedEmailAccountOpensTheVerification() = runTest(dispatcher) {
        val vm = viewModel(flowOf(unverified))
        advanceUntilIdle()
        assertEquals(Start.VerifyEmail, vm.start.value)
    }

    @Test
    fun verifiedSessionOpensHome() = runTest(dispatcher) {
        val vm = viewModel(flowOf(verified))
        advanceUntilIdle()
        assertEquals(Start.Home, vm.start.value)
    }

    // WEL-006: mientras no se sabe la sesión, ni bienvenida ni Inicio
    @Test
    fun staysLoadingUntilTheSessionIsKnown() = runTest(dispatcher) {
        val session = CompletableDeferred<AccountUser?>()
        val vm = viewModel(flow { emit(session.await()) })
        advanceUntilIdle()
        assertEquals(Start.Loading, vm.start.value)

        session.complete(verified)
        advanceUntilIdle()
        assertEquals(Start.Home, vm.start.value)
    }

    // Se decide una vez por arranque: perder la sesión con la app abierta no reubica
    @Test
    fun theDecisionIsTakenOncePerLaunch() = runTest(dispatcher) {
        val user = MutableStateFlow<AccountUser?>(verified)
        val vm = viewModel(user)
        advanceUntilIdle()

        user.value = null
        advanceUntilIdle()

        assertEquals(Start.Home, vm.start.value)
    }
}
