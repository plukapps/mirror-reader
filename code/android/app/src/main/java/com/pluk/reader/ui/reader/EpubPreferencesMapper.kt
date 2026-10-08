package com.pluk.reader.ui.reader

import com.pluk.reader.domain.model.ReaderSettings
import com.pluk.reader.domain.model.ReadingTheme
import org.readium.r2.navigator.epub.EpubPreferences
import org.readium.r2.navigator.preferences.ColumnCount
import org.readium.r2.navigator.preferences.FontFamily
import org.readium.r2.navigator.preferences.Spread
import org.readium.r2.navigator.preferences.Theme
import org.readium.r2.shared.ExperimentalReadiumApi

/** RDR-016: con dos páginas, el margen de Readium es mayor: separa las páginas entre sí. */
internal const val SPREAD_PAGE_MARGINS = 1.0

@OptIn(ExperimentalReadiumApi::class)
fun ReaderSettings.toEpubPreferences(twoPages: Boolean = false): EpubPreferences = EpubPreferences(
    // RDR-001: la lectura es siempre paginada.
    scroll = false,
    // RDR-016: en pantalla ancha y horizontal, dos páginas lado a lado.
    columnCount = if (twoPages) ColumnCount.TWO else ColumnCount.ONE,
    spread = if (twoPages) Spread.ALWAYS else Spread.NEVER,
    theme = when (theme) {
        ReadingTheme.LIGHT -> Theme.LIGHT
        ReadingTheme.DARK -> Theme.DARK
        ReadingTheme.SEPIA -> Theme.SEPIA
    },
    fontSize = fontScale,
    fontFamily = FontFamily(font.familyName),
    lineHeight = lineSpacing.lineHeight,
    // Sin los estilos del editor, Readium aplica la fuente y el interlineado elegidos (ADR 0009).
    publisherStyles = false,
    // Sin margen de Readium: el único margen es el padding del contenedor (READING_PADDING) + esto que le agreuge.
    pageMargins = if (twoPages) SPREAD_PAGE_MARGINS else 0.2,
)
