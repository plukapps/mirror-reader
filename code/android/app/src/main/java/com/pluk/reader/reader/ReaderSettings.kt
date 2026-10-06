package com.pluk.reader.reader

import org.readium.r2.navigator.epub.EpubPreferences
import org.readium.r2.navigator.preferences.Theme
import org.readium.r2.shared.ExperimentalReadiumApi
import kotlin.math.roundToInt

enum class ReaderTheme {
    LIGHT, DARK, SEPIA;

    fun next(): ReaderTheme = entries[(ordinal + 1) % entries.size]
}

data class ReaderSettings(
    val scroll: Boolean = false,
    val theme: ReaderTheme = ReaderTheme.LIGHT,
    val fontScale: Double = 1.0,
) {
    fun toggleScroll() = copy(scroll = !scroll)
    fun nextTheme() = copy(theme = theme.next())
    fun biggerFont() = copy(fontScale = step(+1))
    fun smallerFont() = copy(fontScale = step(-1))

    private fun step(direction: Int): Double =
        ((fontScale * 10).roundToInt() + direction).coerceIn(MIN_TENTHS, MAX_TENTHS) / 10.0

    private companion object {
        const val MIN_TENTHS = 5
        const val MAX_TENTHS = 25
    }
}

@OptIn(ExperimentalReadiumApi::class)
fun ReaderSettings.toEpubPreferences(): EpubPreferences = EpubPreferences(
    scroll = scroll,
    theme = when (theme) {
        ReaderTheme.LIGHT -> Theme.LIGHT
        ReaderTheme.DARK -> Theme.DARK
        ReaderTheme.SEPIA -> Theme.SEPIA
    },
    fontSize = fontScale,
)
