package com.pluk.reader.data.account

import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuthActionCodeException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.pluk.reader.domain.account.AuthError

/** Traduce los errores del SDK de Firebase Auth a [AuthError] (ONB-015, ADR 0014). */
internal fun Throwable.toAuthError(): AuthError = when (this) {
    is FirebaseAuthWeakPasswordException -> AuthError.WeakPassword
    is FirebaseAuthActionCodeException -> AuthError.InvalidLink
    is FirebaseAuthUserCollisionException -> AuthError.EmailInUse
    is FirebaseAuthInvalidUserException ->
        // Sin la protección contra enumeración, Firebase avisa si el email no existe: se muestra igual que una
        // contraseña equivocada para no revelarlo (ONB-015).
        if (errorCode == "ERROR_USER_DISABLED") AuthError.UserDisabled else AuthError.InvalidCredentials
    is FirebaseAuthInvalidCredentialsException ->
        if (errorCode == "ERROR_INVALID_ACTION_CODE" || errorCode == "ERROR_EXPIRED_ACTION_CODE") AuthError.InvalidLink
        else AuthError.InvalidCredentials
    is FirebaseTooManyRequestsException -> AuthError.TooManyRequests
    is FirebaseNetworkException -> AuthError.Network
    else -> AuthError.Unknown
}
