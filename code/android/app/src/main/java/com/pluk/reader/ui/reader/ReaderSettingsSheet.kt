package com.pluk.reader.ui.reader

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pluk.reader.domain.model.LineSpacing
import com.pluk.reader.domain.model.ReaderFont
import com.pluk.reader.domain.model.ReaderSettings
import com.pluk.reader.domain.model.ReadingTheme

// Colores del panel, tomados del diseño (pantalla 07): es oscuro en todos los temas.
private val SheetBackground = Color(0xFF130000)
private val SheetInk = Color(0xFFFDFDFD)
private val SheetAccent = Color(0xFFFBD256)
private val SheetLine = Color(0xFF4A4840)
private val SheetTrack = Color(0xFF33322D)
private val SheetMuted = Color(0xFFA8A496)

/** Fuente de cada opción para dibujar su propia etiqueta; sale de los mismos archivos que usa el libro (ADR 0009). */
@Composable
private fun ReaderFont.labelFamily(): FontFamily {
    val assets = LocalContext.current.assets
    return remember(this) {
        when (this) {
            ReaderFont.SERIF -> FontFamily(Font("fonts/Newsreader.ttf", assets))
            ReaderFont.SANS -> FontFamily(Font("fonts/HostGrotesk.ttf", assets))
            ReaderFont.MONO -> FontFamily(Font("fonts/JetBrainsMono.ttf", assets))
        }
    }
}

private fun ReaderFont.label() = when (this) {
    ReaderFont.SERIF -> "Newsreader"
    ReaderFont.SANS -> "Grotesk"
    ReaderFont.MONO -> "Mono"
}

private fun ReadingTheme.label() = when (this) {
    ReadingTheme.LIGHT -> "Clásico"
    ReadingTheme.SEPIA -> "Sepia"
    ReadingTheme.DARK -> "Noche"
}

private fun ReadingTheme.swatch() = when (this) {
    ReadingTheme.LIGHT -> Color(0xFFFDFDFD)
    ReadingTheme.SEPIA -> Color(0xFFF3E9D4)
    ReadingTheme.DARK -> Color(0xFF000000)
}

/** Acciones del panel. Separadas del `ViewModel` para poder previsualizarlo. */
class ReaderSettingsActions(
    val onDismiss: () -> Unit,
    val onFont: (ReaderFont) -> Unit,
    val onSmallerFont: () -> Unit,
    val onBiggerFont: () -> Unit,
    val onTheme: (ReadingTheme) -> Unit,
    val onLineSpacing: (LineSpacing) -> Unit,
    val onOpenToc: () -> Unit,
    val onTogglePageAnimation: () -> Unit,
) {
    companion object {
        val None = ReaderSettingsActions({}, {}, {}, {}, {}, {}, {}, {})
    }
}

/**
 * RDR-014: panel de ajustes de lectura. Sube desde abajo sobre el libro (que sigue visible) y cada cambio
 * se aplica en el momento. Se cierra tocando fuera, arrastrándolo o con atrás.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderSettingsSheet(settings: ReaderSettings, actions: ReaderSettingsActions) {
    ModalBottomSheet(
        onDismissRequest = actions.onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = SheetBackground,
        contentColor = SheetInk,
        scrimColor = Color.Black.copy(alpha = 0.2f),
        dragHandle = {
            Box(Modifier.padding(top = 10.dp, bottom = 8.dp).width(36.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(SheetLine))
        },
        modifier = Modifier.testTag("reader-settings-sheet"),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp).navigationBarsPadding().padding(bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            FontChips(settings.font, actions.onFont)
            SizeButtons(actions.onSmallerFont, actions.onBiggerFont)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                ThemeSwatches(settings.theme, actions.onTheme)
                SpacingToggle(settings.lineSpacing, actions.onLineSpacing)
            }
            ReadingSection(settings, actions)
        }
    }
}

@Composable
private fun FontChips(selected: ReaderFont, onSelect: (ReaderFont) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ReaderFont.entries.forEach { font ->
            val isSelected = font == selected
            Box(
                Modifier
                    .height(40.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .then(if (isSelected) Modifier.background(SheetAccent) else Modifier.border(BorderStroke(1.5.dp, SheetLine), RoundedCornerShape(20.dp)))
                    .selectableOption(isSelected) { onSelect(font) }
                    .padding(horizontal = 16.dp)
                    .testTag("reader-font-${font.name}"),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    font.label(),
                    color = if (isSelected) SheetBackground else SheetInk,
                    style = TextStyle(fontFamily = font.labelFamily(), fontSize = 15.sp, fontWeight = FontWeight.Medium),
                )
            }
        }
    }
}

@Composable
private fun SizeButtons(onSmaller: () -> Unit, onBigger: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).border(BorderStroke(1.5.dp, SheetLine), RoundedCornerShape(20.dp)),
    ) {
        SizeButton("A−", 14.sp, "Letra más chica", onSmaller, Modifier.weight(1f).testTag("reader-font-smaller"))
        Box(Modifier.width(1.5.dp).height(48.dp).background(SheetLine))
        SizeButton("A+", 22.sp, "Letra más grande", onBigger, Modifier.weight(1f).testTag("reader-font-bigger"))
    }
}

@Composable
private fun SizeButton(text: String, size: androidx.compose.ui.unit.TextUnit, description: String, onClick: () -> Unit, modifier: Modifier) {
    Box(
        modifier.height(48.dp).clickable(role = Role.Button, onClick = onClick).semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = SheetInk, style = TextStyle(fontFamily = ReaderFont.SERIF.labelFamily(), fontSize = size, fontWeight = FontWeight.Medium))
    }
}

@Composable
private fun ThemeSwatches(selected: ReadingTheme, onSelect: (ReadingTheme) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        listOf(ReadingTheme.LIGHT, ReadingTheme.SEPIA, ReadingTheme.DARK).forEach { theme ->
            val isSelected = theme == selected
            Column(
                Modifier.selectableOption(isSelected) { onSelect(theme) }.testTag("reader-theme-${theme.name}"),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                // El aro amarillo del elegido ocupa el mismo lugar que el margen de los otros: nada se corre.
                Box(
                    Modifier.size(54.dp).then(if (isSelected) Modifier.border(2.dp, SheetAccent, CircleShape) else Modifier).padding(5.dp),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .clip(CircleShape)
                            .background(theme.swatch())
                            .then(if (theme == ReadingTheme.DARK && !isSelected) Modifier.border(1.5.dp, SheetLine, CircleShape) else Modifier),
                    )
                }
                Text(
                    theme.label(),
                    color = if (isSelected) SheetAccent else SheetMuted,
                    fontSize = 11.sp,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                )
            }
        }
    }
}

@Composable
private fun SpacingToggle(selected: LineSpacing, onSelect: (LineSpacing) -> Unit) {
    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            SpacingButton(ReaderIcons.LineSpacingWide, "Interlineado amplio", selected == LineSpacing.WIDE, "reader-spacing-WIDE") {
                onSelect(LineSpacing.WIDE)
            }
            SpacingButton(ReaderIcons.LineSpacingNormal, "Interlineado normal", selected == LineSpacing.NORMAL, "reader-spacing-NORMAL") {
                onSelect(LineSpacing.NORMAL)
            }
        }
        Text("Interlineado", color = SheetMuted, fontSize = 11.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun SpacingButton(icon: ImageVector, description: String, isSelected: Boolean, tag: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(10.dp))
            .then(if (isSelected) Modifier.background(SheetTrack) else Modifier)
            .selectableOption(isSelected, onClick)
            .semantics { contentDescription = description }
            .testTag(tag),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = if (isSelected) SheetInk else Color(0xFF8A8676), modifier = Modifier.size(20.dp))
    }
}

/** Índice y animación de página: lo que antes estaba en la barra (RDR-004, RDR-009). */
@Composable
private fun ReadingSection(settings: ReaderSettings, actions: ReaderSettingsActions) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(SheetTrack))
        OptionRow("Índice") {
            PillButton("Abrir", false, "reader-toc-button", actions.onOpenToc)
        }
        OptionRow("Animación de página") {
            Switch(
                checked = settings.pageAnimation,
                onCheckedChange = { actions.onTogglePageAnimation() },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = SheetBackground,
                    checkedTrackColor = SheetAccent,
                    uncheckedThumbColor = SheetMuted,
                    uncheckedTrackColor = SheetTrack,
                    uncheckedBorderColor = SheetLine,
                ),
                modifier = Modifier.testTag("reader-page-animation"),
            )
        }
    }
}

@Composable
private fun OptionRow(label: String, content: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().height(40.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = SheetInk, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        Row(verticalAlignment = Alignment.CenterVertically) { content() }
    }
}

@Composable
private fun PillButton(text: String, isSelected: Boolean, tag: String, onClick: () -> Unit) {
    Box(
        Modifier
            .height(32.dp)
            .clip(RoundedCornerShape(16.dp))
            .then(if (isSelected) Modifier.background(SheetAccent) else Modifier.border(BorderStroke(1.5.dp, SheetLine), RoundedCornerShape(16.dp)))
            .selectableOption(isSelected, onClick)
            .padding(horizontal = 14.dp)
            .testTag(tag),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = if (isSelected) SheetBackground else SheetInk, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

/** Opción de una elección: se toca, y para accesibilidad dice si está elegida. */
private fun Modifier.selectableOption(isSelected: Boolean, onClick: () -> Unit): Modifier =
    clickable(role = Role.RadioButton, onClick = onClick).semantics { selected = isSelected }
