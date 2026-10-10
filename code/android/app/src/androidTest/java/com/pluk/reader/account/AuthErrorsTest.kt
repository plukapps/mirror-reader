package com.pluk.reader.data.account

import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.pluk.reader.domain.account.AuthError
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4

/** ONB-015, ONB-018: errores de Firebase Auth traducidos al dominio (ADR 0014). */
@RunWith(AndroidJUnit4::class)
class AuthErrorsTest {
    @Test
    fun firebaseErrorsMapToDomainErrors() {
        assertEquals(AuthError.InvalidCredentials, FirebaseAuthInvalidCredentialsException("ERROR_INVALID_CREDENTIAL", "x").toAuthError())
        // No se revela que el email no tiene cuenta.
        assertEquals(AuthError.InvalidCredentials, FirebaseAuthInvalidUserException("ERROR_USER_NOT_FOUND", "x").toAuthError())
        assertEquals(AuthError.UserDisabled, FirebaseAuthInvalidUserException("ERROR_USER_DISABLED", "x").toAuthError())
        assertEquals(AuthError.EmailInUse, FirebaseAuthUserCollisionException("ERROR_EMAIL_ALREADY_IN_USE", "x").toAuthError())
        assertEquals(AuthError.WeakPassword, FirebaseAuthWeakPasswordException("ERROR_WEAK_PASSWORD", "x", "r").toAuthError())
        assertEquals(AuthError.InvalidLink, FirebaseAuthInvalidCredentialsException("ERROR_EXPIRED_ACTION_CODE", "x").toAuthError())
        assertEquals(AuthError.TooManyRequests, FirebaseTooManyRequestsException("x").toAuthError())
        assertEquals(AuthError.Network, FirebaseNetworkException("x").toAuthError())
        assertEquals(AuthError.Unknown, IllegalStateException().toAuthError())
    }
}
