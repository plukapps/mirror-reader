package com.pluk.reader.data.account

import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.userProfileChangeRequest
import com.pluk.reader.domain.account.AccountRepository
import com.pluk.reader.domain.account.AccountUser
import com.pluk.reader.domain.account.AuthError
import com.pluk.reader.domain.account.AuthException
import com.pluk.reader.domain.account.AuthRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/** Sesión y cuenta con Firebase Auth: email y contraseña, y Google (ADR 0007, ADR 0014). */
@Singleton
class FirebaseAccountRepository @Inject constructor(
    private val auth: FirebaseAuth,
) : AccountRepository, AuthRepository {

    // El listener del token avisa al iniciar y cerrar sesión, y también al refrescar el token: así una
    // verificación de email (que refresca el token, ONB-007) llega a quien observa la sesión.
    override val user: Flow<AccountUser?> = callbackFlow {
        val listener = FirebaseAuth.IdTokenListener { trySend(it.currentUser?.toAccountUser()) }
        auth.addIdTokenListener(listener)
        awaitClose { auth.removeIdTokenListener(listener) }
    }.distinctUntilChanged()

    override suspend fun signIn(email: String, password: String): Result<AccountUser> =
        signInWithPassword(email, password)

    override suspend fun signInWithPassword(email: String, password: String): Result<AccountUser> = runAuth {
        auth.signInWithEmailAndPassword(email.trim(), password).await().user.required().toAccountUser()
    }

    override suspend fun createAccount(name: String, email: String, password: String): Result<AccountUser> = runAuth {
        val created = auth.createUserWithEmailAndPassword(email.trim(), password).await().user.required()
        created.updateProfile(userProfileChangeRequest { displayName = name.trim() }).await()
        // Si el envío falla, la cuenta ya existe: O3 permite reenviarlo (ONB-007).
        runCatching { created.sendEmailVerification().await() }
        created.toAccountUser()
    }

    override suspend fun signInWithGoogle(idToken: String): Result<AccountUser> = runAuth {
        auth.signInWithCredential(GoogleAuthProvider.getCredential(idToken, null)).await().user.required().toAccountUser()
    }

    override suspend fun sendEmailVerification(): Result<Unit> = runAuth {
        auth.currentUser.required().sendEmailVerification().await()
        Unit
    }

    override suspend fun refreshEmailVerified(): Result<Boolean> = runAuth {
        val current = auth.currentUser.required()
        current.reload().await()
        val verified = auth.currentUser?.isEmailVerified == true
        // El token nuevo lleva email_verified (lo usarán las reglas, K-050) y avisa a quien observa la sesión.
        if (verified) current.getIdToken(true).await()
        verified
    }

    override suspend fun discardUnverifiedAccount(): Result<Unit> = runAuth {
        val current = auth.currentUser
        if (current != null && current.toAccountUser().needsEmailVerification) current.delete().await()
        Unit
    }

    override suspend fun sendPasswordReset(email: String): Result<Unit> {
        val result = runAuth { auth.sendPasswordResetEmail(email.trim()).await(); Unit }
        // ONB-017: se responde igual exista o no la cuenta.
        return if (result.exceptionOrNull()?.let { (it as? AuthException)?.error } == AuthError.InvalidCredentials) {
            Result.success(Unit)
        } else result
    }

    override suspend fun emailForResetCode(code: String): Result<String> = runAuth {
        auth.verifyPasswordResetCode(code).await()
    }

    override suspend fun confirmPasswordReset(code: String, newPassword: String): Result<Unit> = runAuth {
        auth.confirmPasswordReset(code, newPassword).await()
        Unit
    }

    private suspend fun <T> runAuth(block: suspend () -> T): Result<T> =
        try {
            Result.success(block())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(AuthException(e.toAuthError(), e))
        }

    private fun FirebaseUser?.required(): FirebaseUser = this ?: throw IllegalStateException("Firebase no devolvió usuario")

    private fun FirebaseUser.toAccountUser() = AccountUser(
        id = uid,
        email = email,
        displayName = displayName?.takeIf { it.isNotBlank() },
        // Solo las cuentas de email verifican; Google ya llega verificada (ONB-006).
        needsEmailVerification = !isEmailVerified &&
            providerData.any { it.providerId == EmailAuthProvider.PROVIDER_ID } &&
            providerData.none { it.providerId == GoogleAuthProvider.PROVIDER_ID },
    )
}
