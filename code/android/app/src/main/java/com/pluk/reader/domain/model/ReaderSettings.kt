package com.pluk.reader.domain.model

import kotlin.math.roundToInt

enum class ReadingTheme {
    LIGHT, DARK, SEPIA;

    fun next(): ReadingTheme = entries[(ordinal + 1) % entries.size]
}

data class ReaderSettings(
    val scroll: Boolean = false,
    val theme: ReadingTheme = ReadingTheme.LIGHT,
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
