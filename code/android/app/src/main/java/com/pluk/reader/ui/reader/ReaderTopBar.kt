package com.pluk.reader.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pluk.reader.domain.model.ReadingTheme

/** Colores de la barra superior por tema, tomados del diseño (pantallas 06 a 08). */
private class BarColors(val ink: Color, val muted: Color)

private fun ReadingTheme.barColors() = when (this) {
    ReadingTheme.LIGHT -> BarColors(ink = Color(0xFF130000), muted = Color(0xFF6B6650))
    ReadingTheme.SEPIA -> BarColors(ink = Color(0xFF3B2F1E), muted = Color(0xFF7A6A50))
    ReadingTheme.DARK -> BarColors(ink = Color(0xFFCFCAC0), muted = Color(0xFF8A8676))
}

internal fun ReadingTheme.barMutedColor() = barColors().muted

/**
 * RDR-013: barra superior del lector con volver, el título del capítulo y "Aa".
 * Tiene el fondo de la página para que el texto de atrás no se mezcle con la barra.
 */
@Composable
fun ReaderTopBar(
    theme: ReadingTheme,
    chapterTitle: String,
    settingsActive: Boolean,
    onBack: () -> Unit,
    onToggleSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = theme.barColors()
    val pageBackground = theme.pageBackground()
    Row(
        modifier = modifier.fillMaxWidth().background(pageBackground).padding(start = 10.dp, end = 10.dp, top = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(44.dp)
                .clickable(role = Role.Button, onClick = onBack)
                .semantics { contentDescription = "Volver" }
                .testTag("reader-back"),
            contentAlignment = Alignment.Center,
        ) {
            Icon(ReaderIcons.ArrowBack, contentDescription = null, tint = colors.ink, modifier = Modifier.size(24.dp))
        }
        Text(
            text = chapterTitle,
            color = colors.muted,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f).padding(horizontal = 8.dp).testTag("reader-chapter-title"),
        )
        // "Aa" abre el panel de ajustes (RDR-014); activo mientras está abierto.
        Box(
            Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(if (settingsActive) colors.ink else Color.Transparent)
                .clickable(role = Role.Button, onClick = onToggleSettings)
                .semantics { contentDescription = "Ajustes de lectura" }
                .testTag("reader-settings-button"),
            contentAlignment = Alignment.Center,
        ) {
            Text("Aa", color = if (settingsActive) pageBackground else colors.ink, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        }
    }
}
