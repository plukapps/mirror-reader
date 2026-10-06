package com.pluk.reader.domain.repository

import com.pluk.reader.domain.model.ReaderSettings
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    val settings: Flow<ReaderSettings>

    /** Aplica [transform] sobre los ajustes guardados y persiste el resultado. */
    suspend fun update(transform: (ReaderSettings) -> ReaderSettings)
}
