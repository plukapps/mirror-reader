package com.pluk.reader.domain.onboarding

/** Qué reglas de ONB-005 cumple una contraseña. */
data class PasswordCheck(
    val longEnough: Boolean,
    val hasNumber: Boolean,
    val hasSymbol: Boolean,
    val hasUppercase: Boolean,
) {
    /** Reglas cumplidas, de 0 a 4: un tramo del medidor por cada una. */
    val score: Int get() = listOf(longEnough, hasNumber, hasSymbol, hasUppercase).count { it }

    /** ONB-005: al menos 8 caracteres y al menos dos de número, símbolo y mayúscula. */
    val isValid: Boolean get() = longEnough && listOf(hasNumber, hasSymbol, hasUppercase).count { it } >= 2

    val strength: PasswordStrength get() = PasswordStrength.entries[score]
}

enum class PasswordStrength { Empty, Weak, Fair, Good, Strong }

const val MIN_PASSWORD_LENGTH = 8

fun checkPassword(password: String) = PasswordCheck(
    longEnough = password.length >= MIN_PASSWORD_LENGTH,
    hasNumber = password.any { it.isDigit() },
    hasSymbol = password.any { !it.isLetterOrDigit() && !it.isWhitespace() },
    hasUppercase = password.any { it.isUpperCase() },
)

private val EMAIL = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")

/** Formato mínimo: algo@algo.algo, sin espacios (ONB-004). La existencia la decide el servidor. */
fun isValidEmail(email: String): Boolean = EMAIL.matches(email.trim())

/** Primer nombre para saludar en "Ya estás adentro" (ONB-013). */
fun firstName(displayName: String?): String? = displayName?.trim()?.split(Regex("\\s+"))?.firstOrNull()?.takeIf { it.isNotEmpty() }
