package com.pluk.reader.ui.theme

import androidx.compose.ui.graphics.Color

/** Paleta "Editorial bold" del diseño (`design/Margin Ebook App.dc.html`). */
object MarginColors {
    val Ink = Color(0xFF130000)
    val Yellow = Color(0xFFFBD256)
    val Paper = Color(0xFFF1F1F1)
    val Muted = Color(0xFF6B6650)
    val Line = Color(0xFFDDD9CE)
    val CoverBorder = Color(0xFFFFFFFF)

    /** Fondos de las portadas generadas cuando el EPUB no trae una. */
    val Covers = listOf(
        Color(0xFF130000), Color(0xFF1F3A5F), Color(0xFFFBD256), Color(0xFFE45A3F),
        Color(0xFF8B1E1E), Color(0xFF7A8F6A), Color(0xFF2F2E29), Color(0xFFE8E2D3),
    )
}
