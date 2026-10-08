package com.pluk.reader.ui.reader

import com.pluk.reader.domain.model.ReaderSettings
import com.pluk.reader.domain.model.ReadingTheme
import org.readium.r2.navigator.epub.EpubPreferences
import org.readium.r2.navigator.preferences.FontFamily
import org.readium.r2.navigator.preferences.Theme
import org.readium.r2.shared.ExperimentalReadiumApi

@OptIn(ExperimentalReadiumApi::class)
fun ReaderSettings.toEpubPreferences(): EpubPreferences = EpubPreferences(
    // RDR-001: la lectura es siempre paginada.
    scroll = false,
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
    pageMargins = 0.2,
)
