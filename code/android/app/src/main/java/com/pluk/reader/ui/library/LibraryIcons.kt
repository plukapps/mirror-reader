package com.pluk.reader.ui.library

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** Los dos íconos que usa la biblioteca, para no depender de la librería de íconos de Material. */
object LibraryIcons {
    val Add: ImageVector = ImageVector.Builder("Add", 24.dp, 24.dp, 24f, 24f).apply {
        path(fill = SolidColor(Color.Black)) {
            moveTo(19f, 13f)
            horizontalLineToRelative(-6f)
            verticalLineToRelative(6f)
            horizontalLineToRelative(-2f)
            verticalLineToRelative(-6f)
            horizontalLineTo(5f)
            verticalLineToRelative(-2f)
            horizontalLineToRelative(6f)
            verticalLineTo(5f)
            horizontalLineToRelative(2f)
            verticalLineToRelative(6f)
            horizontalLineToRelative(6f)
            close()
        }
    }.build()

    val Check: ImageVector = ImageVector.Builder("Check", 24.dp, 24.dp, 24f, 24f).apply {
        path(fill = SolidColor(Color.Black)) {
            moveTo(9f, 16.2f)
            lineTo(4.8f, 12f)
            lineToRelative(-1.4f, 1.4f)
            lineTo(9f, 19f)
            lineTo(21f, 7f)
            lineToRelative(-1.4f, -1.4f)
            close()
        }
    }.build()
}
