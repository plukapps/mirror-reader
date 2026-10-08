package com.pluk.reader.domain.model

import kotlin.math.roundToInt

enum class ReadingTheme { LIGHT, DARK, SEPIA }

/** RDR-014: tipo de letra del libro. Cada valor se asocia a una fuente que viaja en la app (ADR 0009). */
enum class ReaderFont(val familyName: String) {
    SERIF("Newsreader"),
    SANS("Host Grotesk"),
    MONO("JetBrains Mono"),
}

/** RDR-014: interlineado del libro. */
enum class LineSpacing(val lineHeight: Double) {
    NORMAL(1.4),
    WIDE(1.7),
}

data class ReaderSettings(
    val theme: ReadingTheme = ReadingTheme.LIGHT,
    val fontScale: Double = 1.0,
    /** RDR-009: animación de paso de página en modo paginado. */
    val pageAnimation: Boolean = true,
    val font: ReaderFont = ReaderFont.SERIF,
    val lineSpacing: LineSpacing = LineSpacing.NORMAL,
) {
    fun togglePageAnimation() = copy(pageAnimation = !pageAnimation)
    fun withTheme(theme: ReadingTheme) = copy(theme = theme)
    fun withFont(font: ReaderFont) = copy(font = font)
    fun withLineSpacing(lineSpacing: LineSpacing) = copy(lineSpacing = lineSpacing)
    fun biggerFont() = copy(fontScale = step(+1))
    fun smallerFont() = copy(fontScale = step(-1))

    private fun step(direction: Int): Double =
        ((fontScale * 10).roundToInt() + direction).coerceIn(MIN_TENTHS, MAX_TENTHS) / 10.0

    private companion object {
        const val MIN_TENTHS = 5
        const val MAX_TENTHS = 25
    }
}
