package com.pluk.reader.data.account

import com.pluk.reader.domain.account.AccountRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/** Credenciales de la cuenta de desarrollo. Vienen de `local.properties`, solo en builds debug. */
data class DevCredentials(val email: String, val password: String) {
    // La contraseña nunca debe llegar a un log por un `toString` accidental.
    override fun toString() = "DevCredentials(email=$email, password=***)"
}

sealed interface DevSignInOutcome {
    data object NotConfigured : DevSignInOutcome
    data class AlreadySignedIn(val userId: String) : DevSignInOutcome
    data class SignedIn(val userId: String) : DevSignInOutcome
    data class Failed(val reason: String?) : DevSignInOutcome
}

/**
 * Inicia sesión con la cuenta de desarrollo mientras no existan las pantallas de cuenta.
 * Si faltan las credenciales, la app sigue funcionando solo en local (ADR 0002).
 */
class DevAccountSignIn @Inject constructor(
    private val accounts: AccountRepository,
    private val credentials: DevCredentials,
) {
    suspend fun signInIfConfigured(): DevSignInOutcome {
        if (credentials.email.isBlank() || credentials.password.isBlank()) return DevSignInOutcome.NotConfigured
        accounts.user.first()?.let { return DevSignInOutcome.AlreadySignedIn(it.id) }
        return accounts.signIn(credentials.email, credentials.password).fold(
            onSuccess = { DevSignInOutcome.SignedIn(it.id) },
            onFailure = { DevSignInOutcome.Failed(it.message) },
        )
    }
}
