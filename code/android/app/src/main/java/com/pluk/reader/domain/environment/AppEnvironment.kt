package com.pluk.reader.domain.environment

/**
 * Entorno en el que corre la app (AND-007, AND-008, ADR 0020). Lo declara cada flavor del build y lo provee Hilt;
 * el dominio no lee `BuildConfig`.
 */
enum class AppEnvironment(val id: String) {
    DEV("dev"),
    STAGE("stage"),
    PROD("prod");

    val isProduction: Boolean get() = this == PROD

    /** Versión para mostrar: fuera de producción lleva el entorno, así se sabe qué build está abierto. */
    fun versionLabel(versionName: String): String = if (isProduction) versionName else "$versionName · $id"

    companion object {
        /** Falla con un nombre desconocido: un flavor mal configurado no debe caer en otro entorno. */
        fun fromName(name: String): AppEnvironment =
            entries.firstOrNull { it.id == name } ?: throw IllegalArgumentException("Entorno desconocido: $name")
    }
}
