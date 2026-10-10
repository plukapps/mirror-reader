package com.pluk.reader.data.store

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.pluk.reader.domain.store.StoreAcquisitions
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Libros obtenidos de la tienda (STO-008), guardados como `clave de la obra<TAB>id del libro`. */
class StoreAcquisitionsImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : StoreAcquisitions {
    override val acquired: Flow<Map<String, String>> = dataStore.data.map { prefs ->
        prefs[ACQUIRED].orEmpty().mapNotNull { entry ->
            val parts = entry.split('\t')
            if (parts.size == 2) parts[0] to parts[1] else null
        }.toMap()
    }

    override suspend fun record(workKey: String, bookId: String) {
        dataStore.edit { prefs ->
            val others = prefs[ACQUIRED].orEmpty().filterNot { it.startsWith("$workKey\t") }.toSet()
            prefs[ACQUIRED] = others + "$workKey\t$bookId"
        }
    }

    private companion object {
        val ACQUIRED = stringSetPreferencesKey("store_acquired")
    }
}
