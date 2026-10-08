package com.pluk.reader.ui.reader

import com.pluk.reader.domain.model.ReaderFont
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.navigator.epub.css.FontStyle
import org.readium.r2.navigator.preferences.FontFamily
import org.readium.r2.shared.ExperimentalReadiumApi

/** Un archivo de fuente en `assets/fonts` y el rango de pesos que cubre (todas son fuentes variables, ADR 0009). */
private data class FontFile(val path: String, val style: FontStyle, val weights: IntRange)

private val FONT_FILES: Map<ReaderFont, List<FontFile>> = mapOf(
    ReaderFont.SERIF to listOf(
        FontFile("fonts/Newsreader.ttf", FontStyle.NORMAL, 200..800),
        FontFile("fonts/Newsreader-Italic.ttf", FontStyle.ITALIC, 200..800),
    ),
    ReaderFont.SANS to listOf(
        FontFile("fonts/HostGrotesk.ttf", FontStyle.NORMAL, 300..800),
        FontFile("fonts/HostGrotesk-Italic.ttf", FontStyle.ITALIC, 300..800),
    ),
    ReaderFont.MONO to listOf(
        FontFile("fonts/JetBrainsMono.ttf", FontStyle.NORMAL, 100..800),
    ),
)

/**
 * Configuración del navegador de EPUB: sirve las fuentes de la app al libro y declara cada familia
 * (RDR-014, ADR 0009). Los insets de sistema (barra de estado) los reserva ReaderScreen, no Readium.
 */
@OptIn(ExperimentalReadiumApi::class)
internal fun readerNavigatorConfiguration() = EpubNavigatorFragment.Configuration(
    servedAssets = listOf("fonts/.*"),
    shouldApplyInsetsPadding = false,
).apply {
    FONT_FILES.forEach { (font, files) ->
        addFontFamilyDeclaration(FontFamily(font.familyName)) {
            files.forEach { file ->
                addFontFace {
                    addSource(file.path)
                    setFontStyle(file.style)
                    setFontWeight(file.weights)
                }
            }
        }
    }
}
