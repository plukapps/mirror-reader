package com.pluk.reader.domain.account

import java.net.URI
import java.net.URLDecoder

/** Enlace de acción de un email de la cuenta, abierto en la app (ONB-007, ONB-018, ADR 0014). */
sealed interface AuthLink {
    data class ResetPassword(val code: String) : AuthLink

    data class VerifyEmail(val code: String) : AuthLink
}

/**
 * Lee un enlace `https://<host>/__/auth/action?mode=…&oobCode=…`. Devuelve null si no es un enlace de acción
 * de [host] o si su modo no se maneja en la app.
 */
fun parseAuthLink(link: String, host: String): AuthLink? {
    val uri = runCatching { URI(link) }.getOrNull() ?: return null
    if (uri.scheme != "https" || !uri.host.equals(host, ignoreCase = true) || uri.path != ACTION_PATH) return null
    val params = uri.rawQuery.orEmpty().split('&').mapNotNull { pair ->
        val parts = pair.split('=', limit = 2)
        if (parts.size == 2) decode(parts[0]) to decode(parts[1]) else null
    }.toMap()
    val code = params["oobCode"]?.takeIf { it.isNotBlank() } ?: return null
    return when (params["mode"]) {
        "resetPassword" -> AuthLink.ResetPassword(code)
        "verifyEmail" -> AuthLink.VerifyEmail(code)
        else -> null
    }
}

private const val ACTION_PATH = "/__/auth/action"

private fun decode(value: String) = URLDecoder.decode(value, Charsets.UTF_8.name())
