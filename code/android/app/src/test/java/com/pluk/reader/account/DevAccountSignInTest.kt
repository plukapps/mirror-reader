package com.pluk.reader.account

import com.pluk.reader.data.account.DevAccountSignIn
import com.pluk.reader.data.account.DevCredentials
import com.pluk.reader.data.account.DevSignInOutcome
import com.pluk.reader.domain.account.AccountRepository
import com.pluk.reader.domain.account.AccountUser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/** Cuenta de desarrollo (ACC-001 en su versión mínima, ACC-004): sin credenciales la app sigue funcionando local (ADR 0002). */
class DevAccountSignInTest {
    private class FakeAccountRepository(initial: AccountUser? = null, private val failWith: Throwable? = null) : AccountRepository {
        private val state = MutableStateFlow(initial)
        override val user: Flow<AccountUser?> = state
        val signIns = mutableListOf<Pair<String, String>>()

        override suspend fun signIn(email: String, password: String): Result<AccountUser> {
            signIns += email to password
            failWith?.let { return Result.failure(it) }
            return Result.success(AccountUser("uid-1", email)).also { state.value = it.getOrNull() }
        }
    }

    private val credentials = DevCredentials("dev@example.com", "secreta")

    @Test
    fun `inicia sesion con las credenciales cuando no hay sesion`() = runTest {
        val repo = FakeAccountRepository()
        val outcome = DevAccountSignIn(repo, credentials).signInIfConfigured()
        assertEquals(DevSignInOutcome.SignedIn("uid-1"), outcome)
        assertEquals(listOf("dev@example.com" to "secreta"), repo.signIns)
    }

    @Test
    fun `sin credenciales no intenta nada`() = runTest {
        val repo = FakeAccountRepository()
        for (blank in listOf(DevCredentials("", ""), DevCredentials("dev@example.com", ""), DevCredentials("", "x"))) {
            assertEquals(DevSignInOutcome.NotConfigured, DevAccountSignIn(repo, blank).signInIfConfigured())
        }
        assertEquals(emptyList<Pair<String, String>>(), repo.signIns)
    }

    @Test
    fun `si ya hay sesion no vuelve a iniciar`() = runTest {
        val repo = FakeAccountRepository(initial = AccountUser("uid-9", "dev@example.com"))
        val outcome = DevAccountSignIn(repo, credentials).signInIfConfigured()
        assertEquals(DevSignInOutcome.AlreadySignedIn("uid-9"), outcome)
        assertEquals(emptyList<Pair<String, String>>(), repo.signIns)
    }

    @Test
    fun `una falla de inicio de sesion no lanza excepcion y no deja sesion`() = runTest {
        val repo = FakeAccountRepository(failWith = IllegalStateException("sin red"))
        val outcome = DevAccountSignIn(repo, credentials).signInIfConfigured()
        assertEquals(DevSignInOutcome.Failed("sin red"), outcome)
    }

    @Test
    fun `las credenciales no aparecen al imprimirlas`() {
        assertEquals(false, credentials.toString().contains("secreta"))
    }
}
