package com.pluk.reader.reader

import android.content.Context
import org.json.JSONObject
import org.readium.r2.shared.publication.Locator
import kotlin.math.roundToInt

interface LocatorStore {
    fun load(bookId: String): Locator?
    fun save(bookId: String, locator: Locator)
}

interface SettingsStore {
    fun load(): ReaderSettings
    fun save(settings: ReaderSettings)
}

/** Posición de lectura por libro, en el dispositivo (RDR-006). La sincronización llega con la rebanada de sync. */
class PrefsLocatorStore(context: Context) : LocatorStore {
    private val prefs = context.applicationContext.getSharedPreferences("reader_locators", Context.MODE_PRIVATE)

    override fun load(bookId: String): Locator? =
        prefs.getString(bookId, null)?.let { runCatching { Locator.fromJSON(JSONObject(it)) }.getOrNull() }

    override fun save(bookId: String, locator: Locator) {
        prefs.edit().putString(bookId, locator.toJSON().toString()).apply()
    }
}

class PrefsSettingsStore(context: Context) : SettingsStore {
    private val prefs = context.applicationContext.getSharedPreferences("reader_settings", Context.MODE_PRIVATE)

    override fun load(): ReaderSettings {
        val defaults = ReaderSettings()
        return ReaderSettings(
            scroll = prefs.getBoolean("scroll", defaults.scroll),
            theme = runCatching { ReaderTheme.valueOf(prefs.getString("theme", null) ?: "") }
                .getOrDefault(defaults.theme),
            fontScale = prefs.getInt("fontTenths", (defaults.fontScale * 10).roundToInt()) / 10.0,
        )
    }

    override fun save(settings: ReaderSettings) {
        prefs.edit()
            .putBoolean("scroll", settings.scroll)
            .putString("theme", settings.theme.name)
            // En décimas enteras: un Float degradaría 1.4 a 1.3999999.
            .putInt("fontTenths", (settings.fontScale * 10).roundToInt())
            .apply()
    }
}
