package com.pluk.reader.ui.onboarding

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import com.pluk.reader.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Resultado de pedirle al sistema una cuenta de Google (ONB-020). */
sealed interface GoogleIdResult {
    data class Token(val idToken: String) : GoogleIdResult

    data object Cancelled : GoogleIdResult

    data object NoAccounts : GoogleIdResult

    data object Failed : GoogleIdResult
}

/**
 * Pide un token de Google con Credential Manager (ADR 0014, firebase.google.com/docs/auth/android/google-signin).
 * [context] debe ser la actividad: el sistema muestra encima el selector de cuentas.
 */
suspend fun requestGoogleIdToken(context: Context): GoogleIdResult {
    val option = GetGoogleIdOption.Builder()
        .setServerClientId(context.getString(R.string.default_web_client_id))
        // Alta e ingreso usan el mismo botón: se ofrecen todas las cuentas del dispositivo.
        .setFilterByAuthorizedAccounts(false)
        .build()
    val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
    return try {
        val credential = CredentialManager.create(context).getCredential(context, request).credential
        if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            GoogleIdResult.Token(GoogleIdTokenCredential.createFrom(credential.data).idToken)
        } else {
            GoogleIdResult.Failed
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: GetCredentialCancellationException) {
        GoogleIdResult.Cancelled
    } catch (e: NoCredentialException) {
        GoogleIdResult.NoAccounts
    } catch (e: GetCredentialException) {
        GoogleIdResult.Failed
    } catch (e: GoogleIdTokenParsingException) {
        GoogleIdResult.Failed
    }
}

/**
 * Acción del botón de Google: pide el token y lo entrega a [onToken]. Cancelar no muestra nada (ONB-020); sin
 * cuentas avisa con un toast; otro fallo llama a [onFailed].
 */
@Composable
internal fun rememberGoogleSignIn(onToken: (String) -> Unit, onFailed: () -> Unit): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    return {
        scope.launch {
            when (val result = requestGoogleIdToken(context)) {
                is GoogleIdResult.Token -> onToken(result.idToken)
                GoogleIdResult.Cancelled -> Unit
                GoogleIdResult.NoAccounts -> Toast.makeText(context, R.string.onb_google_unavailable, Toast.LENGTH_LONG).show()
                GoogleIdResult.Failed -> onFailed()
            }
        }
    }
}

/** Abre la app de correo en su bandeja (ONB-007, ONB-017). */
internal fun openEmailApp(context: Context) {
    val intent = Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_EMAIL)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, R.string.onb_no_email_app, Toast.LENGTH_SHORT).show()
    }
}
