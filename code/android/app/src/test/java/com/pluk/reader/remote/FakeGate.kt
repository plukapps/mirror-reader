package com.pluk.reader.remote

import com.pluk.reader.domain.sync.SyncGate
import kotlinx.coroutines.flow.MutableStateFlow

/** Puerta de sincronización controlada por el test (PRF-023). */
class FakeGate(open: Boolean = true) : SyncGate {
    val state = MutableStateFlow(open)
    override suspend fun isOpen() = state.value
    override val open = state
}
