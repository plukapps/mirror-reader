package com.pluk.reader.flags

import com.pluk.reader.domain.flags.Feature
import com.pluk.reader.domain.flags.FeatureFlags
import com.pluk.reader.domain.flags.Setting
import kotlinx.coroutines.flow.MutableStateFlow

/** Funciones con sus valores por defecto, que el test puede cambiar. */
class FakeFeatureFlags(vararg on: Feature) : FeatureFlags {
    private val states = Feature.entries.associateWith { MutableStateFlow(it.default || it in on) }

    private val settings = Setting.entries.associateWith { MutableStateFlow(it.default) }

    override fun state(feature: Feature) = states.getValue(feature)

    override fun setting(setting: Setting) = settings.getValue(setting)

    fun set(setting: Setting, value: Long) {
        settings.getValue(setting).value = value
    }

    fun set(feature: Feature, value: Boolean) {
        states.getValue(feature).value = value
    }
}
