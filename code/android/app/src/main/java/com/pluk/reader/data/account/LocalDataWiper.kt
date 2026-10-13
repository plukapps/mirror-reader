package com.pluk.reader.data.account

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.pluk.reader.data.library.LibraryFiles
import com.pluk.reader.data.local.db.ReaderDatabase
import com.pluk.reader.domain.account.LocalAccountData
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * PRF-009: borra de este dispositivo lo que es de la cuenta: la base (libros, posiciones, quitados), los archivos
 * de libros y portadas y las preferencias de la cuenta (sincronización, onboarding, espacio guardado).
 * Quedan los ajustes del lector y la identidad del dispositivo, que no son de la cuenta.
 *
 * Cada parte se intenta aunque falle otra; al final se informa el primer error.
 */
class LocalDataWiper @Inject constructor(
    private val db: ReaderDatabase,
    private val files: LibraryFiles,
    private val dataStore: DataStore<Preferences>,
) : LocalAccountData {
    override suspend fun wipe() {
        val failures = listOf(
            runCatching { withContext(Dispatchers.IO) { db.clearAllTables() } },
            runCatching { withContext(Dispatchers.IO) { files.deleteAll() } },
            runCatching {
                dataStore.edit { prefs ->
                    prefs.asMap().keys.filter { it.name !in RETAINED_KEYS }.forEach { prefs.remove(it) }
                }
            },
        ).mapNotNull { it.exceptionOrNull() }
        failures.firstOrNull()?.let { throw it }
    }

    companion object {
        /**
         * Claves del DataStore que sobreviven al cierre de sesión: los ajustes del lector (`SettingsRepositoryImpl`)
         * el id de esta instalación (`SyncPreferencesImpl`) y la apariencia (`AppearancePreferencesImpl`, PRF-021). Todo lo demás se borra, también lo que se agregue
         * más adelante, salvo que se sume aquí.
         */
        val RETAINED_KEYS = setOf("theme", "fontTenths", "pageAnimation", "font", "lineSpacing", "sync_device_id", "appearance")
    }
}
