package com.pluk.reader.profile

import com.pluk.reader.domain.account.LocalAccountData
import com.pluk.reader.domain.onboarding.DailyReminder
import com.pluk.reader.domain.onboarding.ReadingGoal
import com.pluk.reader.domain.onboarding.ReminderScheduler
import com.pluk.reader.domain.usecase.CloudListener
import com.pluk.reader.domain.usecase.LibraryPassGate
import com.pluk.reader.domain.usecase.SignOut
import com.pluk.reader.onboarding.FakeAuth
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SignOutTest {
    private val events = mutableListOf<String>()

    private inner class Listener(private val name: String) : CloudListener {
        override fun startListening() {
            events += "start:$name"
        }

        override fun stopListening() {
            events += "stop:$name"
        }
    }

    private inner class Gate : LibraryPassGate {
        override suspend fun <T> whileIdle(block: suspend () -> T): T {
            events += "idle"
            return block().also { events += "resume" }
        }
    }

    private inner class Data(private val fails: Boolean = false) : LocalAccountData {
        override suspend fun wipe() {
            events += "wipe"
            if (fails) error("disco lleno")
        }
    }

    private inner class Reminders : ReminderScheduler {
        override fun schedule(reminder: DailyReminder, goal: ReadingGoal?) = Unit
        override fun cancel() {
            events += "cancelReminder"
        }
        override fun canNotify() = true
    }

    private val auth = object : FakeAuth() {
        override suspend fun signOut(): Result<Unit> {
            events += "signOut"
            return super.signOut()
        }
    }

    private fun signOut(pending: Boolean = false, wipeFails: Boolean = false) = SignOut(
        listeners = listOf(Listener("positions"), Listener("cloud")),
        gate = Gate(),
        localData = Data(wipeFails),
        auth = auth,
        reminders = Reminders(),
        hasPending = { pending },
    )

    // PRF-009: primero se deja de escuchar y se espera a que no haya pasadas; después se borra y recién al final
    // se cierra la sesión. Así ninguna escucha ni pasada vuelve a escribir datos de la cuenta después del borrado.
    // Las escuchas vuelven a encenderse sin sesión (no hacen nada) para que la próxima cuenta las tenga.
    @Test
    fun stopsSyncThenWipesThenSignsOut() = runTest {
        val result = signOut()()
        assertTrue(result.isSuccess)
        assertEquals(
            listOf("stop:positions", "stop:cloud", "idle", "wipe", "cancelReminder", "signOut", "resume", "start:positions", "start:cloud"),
            events,
        )
    }

    // PRF-009: si el borrado falla, igual se cierra la sesión (la sincronización reconstruye al volver a entrar)
    @Test
    fun signsOutEvenIfWipeFails() = runTest {
        val result = signOut(wipeFails = true)()
        assertTrue(result.isSuccess)
        assertTrue("signOut" in events)
    }

    // PRF-009: la confirmación avisa si hay cambios sin subir
    @Test
    fun reportsPendingChanges() = runTest {
        assertTrue(signOut(pending = true).hasPendingChanges())
        assertFalse(signOut(pending = false).hasPendingChanges())
    }

    // PRF-009: si cerrar la sesión falla, se informa
    @Test
    fun reportsSignOutFailure() = runTest {
        auth.signOutResult = Result.failure(IllegalStateException("sin red"))
        assertTrue(signOut()().isFailure)
    }
}
