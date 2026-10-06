package com.pluk.reader.ui.reader

import com.pluk.reader.domain.model.ReaderSettings
import com.pluk.reader.domain.model.ReadingTheme
import org.readium.r2.navigator.epub.EpubPreferences
import org.readium.r2.navigator.preferences.Theme
import org.readium.r2.shared.ExperimentalReadiumApi

@OptIn(ExperimentalReadiumApi::class)
fun ReaderSettings.toEpubPreferences(): EpubPreferences = EpubPreferences(
    scroll = scroll,
    theme = when (theme) {
        ReadingTheme.LIGHT -> Theme.LIGHT
        ReadingTheme.DARK -> Theme.DARK
        ReadingTheme.SEPIA -> Theme.SEPIA
    },
    fontSize = fontScale,
)
