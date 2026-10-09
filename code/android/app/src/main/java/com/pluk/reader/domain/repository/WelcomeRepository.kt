package com.pluk.reader.domain.repository

import kotlinx.coroutines.flow.Flow

/** Si el usuario ya tocó "Comenzar" en la bienvenida (WEL-003, WEL-005). */
interface WelcomeRepository {
    val completed: Flow<Boolean>

    suspend fun markCompleted()
}
