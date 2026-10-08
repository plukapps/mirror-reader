package com.pluk.reader.ui.reader

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** Íconos del lector, dibujados aquí para no depender de la librería de íconos de Material (como `LibraryIcons`). */
object ReaderIcons {
    val ArrowBack: ImageVector = ImageVector.Builder("ArrowBack", 24.dp, 24.dp, 24f, 24f).apply {
        path(fill = SolidColor(Color.Black)) {
            moveTo(20f, 11f)
            horizontalLineTo(7.83f)
            lineToRelative(5.59f, -5.59f)
            lineTo(12f, 4f)
            lineToRelative(-8f, 8f)
            lineToRelative(8f, 8f)
            lineToRelative(1.41f, -1.41f)
            lineTo(7.83f, 13f)
            horizontalLineTo(20f)
            close()
        }
    }.build()

    /** Interlineado normal: tres líneas juntas. */
    val LineSpacingNormal: ImageVector = ImageVector.Builder("LineSpacingNormal", 24.dp, 24.dp, 24f, 24f).apply {
        path(fill = SolidColor(Color.Black)) {
            bar(7f)
            bar(11f)
            bar(15f)
        }
    }.build()

    /** Interlineado amplio: tres líneas separadas. */
    val LineSpacingWide: ImageVector = ImageVector.Builder("LineSpacingWide", 24.dp, 24.dp, 24f, 24f).apply {
        path(fill = SolidColor(Color.Black)) {
            bar(4f)
            bar(11f)
            bar(18f)
        }
    }.build()

    private fun androidx.compose.ui.graphics.vector.PathBuilder.bar(top: Float) {
        moveTo(4f, top)
        horizontalLineTo(20f)
        verticalLineToRelative(2f)
        horizontalLineTo(4f)
        close()
    }
}
