package com.pluk.reader.domain.account

import kotlinx.coroutines.flow.Flow

/** Usuario con sesión iniciada. [id] es el `uid` del backend (ACC-004). */
data class AccountUser(val id: String, val email: String?)

interface AccountRepository {
    /** Usuario actual, o null si no hay sesión. Emite en cada cambio de sesión. */
    val user: Flow<AccountUser?>

    /** Inicia sesión con email y contraseña (ACC-001). Nunca lanza: el fallo viaja en el `Result`. */
    suspend fun signIn(email: String, password: String): Result<AccountUser>
}
