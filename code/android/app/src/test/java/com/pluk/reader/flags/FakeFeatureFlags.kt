package com.pluk.reader.flags

import com.pluk.reader.domain.flags.Feature
import com.pluk.reader.domain.flags.FeatureFlags
import kotlinx.coroutines.flow.MutableStateFlow

/** Funciones con sus valores por defecto, que el test puede cambiar. */
class FakeFeatureFlags(vararg on: Feature) : FeatureFlags {
    private val states = Feature.entries.associateWith { MutableStateFlow(it.default || it in on) }

    override fun state(feature: Feature) = states.getValue(feature)

    fun set(feature: Feature, value: Boolean) {
        states.getValue(feature).value = value
    }
}
