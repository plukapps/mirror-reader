package com.pluk.reader.crash

import com.pluk.reader.domain.account.AccountUser
import com.pluk.reader.domain.crash.CrashReporter
import com.pluk.reader.domain.crash.CrashReportIdentity
import com.pluk.reader.onboarding.FakeAuth
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/** CMP-010: los cierres llevan solo el identificador interno de la cuenta, y nada después de cerrar sesión. */
@OptIn(ExperimentalCoroutinesApi::class)
class CrashReportIdentityTest {
    private class FakeReporter : CrashReporter {
        val ids = mutableListOf<String>()
        override fun setUserId(id: String) {
            ids += id
        }
    }

    private fun TestScope.start(auth: FakeAuth, reporter: FakeReporter) {
        CrashReportIdentity(auth, reporter, backgroundScope).start()
        runCurrent()
    }

    @Test
    fun `CMP-010 with a session the reports carry the uid and never the email`() = runTest {
        val auth = FakeAuth()
        val reporter = FakeReporter()
        auth.user.value = AccountUser("u1", "ana@mail.com", "Ana Ruiz")
        start(auth, reporter)

        assertEquals(listOf("u1"), reporter.ids)
    }

    @Test
    fun `CMP-010 signing out clears the id and the same uid is not sent twice`() = runTest {
        val auth = FakeAuth()
        val reporter = FakeReporter()
        start(auth, reporter)

        auth.user.value = AccountUser("u1", "ana@mail.com")
        runCurrent()
        auth.user.value = AccountUser("u1", "ana@mail.com", needsEmailVerification = true)
        runCurrent()
        auth.user.value = null
        runCurrent()

        assertEquals(listOf("", "u1", ""), reporter.ids)
    }
}
