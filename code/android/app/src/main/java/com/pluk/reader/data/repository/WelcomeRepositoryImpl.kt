package com.pluk.reader.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import com.pluk.reader.domain.repository.WelcomeRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/** Guarda la marca de la bienvenida junto a las preferencias de la app (WEL-005). */
class WelcomeRepositoryImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : WelcomeRepository {

    override val completed: Flow<Boolean> = dataStore.data.map { it[COMPLETED] ?: false }

    override suspend fun markCompleted() {
        dataStore.edit { it[COMPLETED] = true }
    }

    private companion object {
        val COMPLETED = booleanPreferencesKey("welcomeCompleted")
    }
}
