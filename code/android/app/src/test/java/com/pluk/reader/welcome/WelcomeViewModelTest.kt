package com.pluk.reader.welcome

import com.pluk.reader.onboarding.FakeWelcomePreference
import com.pluk.reader.ui.welcome.WelcomeViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WelcomeViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    // GST-002: "Empezar a leer" registra la bienvenida como vista antes de seguir
    @Test
    fun startReadingMarksTheWelcomeSeenBeforeContinuing() = runTest(dispatcher) {
        val welcome = FakeWelcomePreference()
        var seenWhenContinuing: Boolean? = null

        WelcomeViewModel(welcome).startReading { seenWhenContinuing = welcome.seen.value }
        advanceUntilIdle()

        assertEquals(1, welcome.marks)
        assertTrue(seenWhenContinuing == true)
    }
}
