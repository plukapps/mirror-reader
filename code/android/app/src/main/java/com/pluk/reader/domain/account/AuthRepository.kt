package com.pluk.reader.domain.account

/** Por qué falló una operación de cuenta, sin nombrar el backend (ONB-015, ADR 0014). */
enum class AuthError {
    /** Email o contraseña incorrectos. No distingue si el email tiene cuenta (ONB-015). */
    InvalidCredentials,
    EmailInUse,
    UserDisabled,
    TooManyRequests,
    Network,

    /** El enlace para cambiar la contraseña venció o ya se usó (ONB-018). */
    InvalidLink,
    WeakPassword,
    Unknown,
}

/** Falla de [AuthRepository]. Viaja dentro del `Result`. */
class AuthException(val error: AuthError, cause: Throwable? = null) : Exception(error.name, cause)

/** El error de un `Result` fallido de [AuthRepository]. */
fun Throwable.authError(): AuthError = (this as? AuthException)?.error ?: AuthError.Unknown

/**
 * Alta, ingreso, verificación y recuperación de la cuenta (ONB, ADR 0014). Ningún método lanza: el fallo viaja
 * en el `Result` como [AuthException]. La sesión que resulta se observa en [AccountRepository.user].
 */
interface AuthRepository {
    /** Crea la cuenta con email, guarda el nombre y envía el email de verificación (ONB-006). */
    suspend fun createAccount(name: String, email: String, password: String): Result<AccountUser>

    suspend fun signInWithPassword(email: String, password: String): Result<AccountUser>

    /** Inicia sesión (o crea la cuenta) con el token de Google que entregó el sistema (ONB-004, ONB-014). */
    suspend fun signInWithGoogle(idToken: String): Result<AccountUser>

    suspend fun sendEmailVerification(): Result<Unit>

    /** Vuelve a preguntar al servidor si el email del usuario actual ya se verificó (ONB-007). */
    suspend fun refreshEmailVerified(): Result<Boolean>

    /** Borra la cuenta actual si su email no está verificado (ONB-008). */
    suspend fun discardUnverifiedAccount(): Result<Unit>

    /** Envía el enlace para elegir una contraseña nueva (ONB-017). */
    suspend fun sendPasswordReset(email: String): Result<Unit>

    /** Email de la cuenta a la que pertenece el enlace, o [AuthError.InvalidLink] (ONB-018). */
    suspend fun emailForResetCode(code: String): Result<String>

    suspend fun confirmPasswordReset(code: String, newPassword: String): Result<Unit>
}
