package com.pluk.reader.onboarding

import com.pluk.reader.domain.account.AccountUser
import com.pluk.reader.domain.model.ImportOutcome
import com.pluk.reader.domain.model.LibraryBook
import com.pluk.reader.domain.repository.LibraryRepository
import com.pluk.reader.remote.FakeSession
import com.pluk.reader.ui.navigation.Routes
import com.pluk.reader.ui.onboarding.ShortAllSetViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/** GST-011, GST-021: alta corta desde adentro de la app. */
@OptIn(ExperimentalCoroutinesApi::class)
class ShortSignUpTest {
    private val dispatcher = StandardTestDispatcher()

    private class Books(initial: List<LibraryBook>) : LibraryRepository {
        override val books = MutableStateFlow(initial)
        override suspend fun import(uri: String): ImportOutcome = ImportOutcome.Rejected("x")
    }

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    // GST-011: con la cuenta creada, el alta corta va a B3 y no a la meta diaria; el alta completa sigue a O5
    @Test
    fun afterTheAccountTheShortFlowSkipsTheGoal() {
        assertEquals(Routes.SHORT_ALL_SET, Routes.afterAccountCreated(inApp = true))
        assertEquals(Routes.GOAL, Routes.afterAccountCreated(inApp = false))
    }

    // GST-021: B3 muestra el nombre y cuántos libros pasan a la cuenta
    @Test
    fun shortAllSetShowsTheNameAndTheLocalBooks() = runTest(dispatcher) {
        val books = listOf(LibraryBook("a", "A", null, null, null), LibraryBook("b", "B", null, null, null))
        val vm = ShortAllSetViewModel(FakeSession(AccountUser("u1", "ana@mail.com", "Ana Ruiz")), Books(books))
        backgroundScope.launch { vm.state.collect {} }
        advanceUntilIdle()
        assertEquals(ShortAllSetViewModel.UiState("Ana", 2), vm.state.value)
    }
}
