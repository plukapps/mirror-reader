package com.pluk.reader.ui.profile

import com.pluk.reader.domain.plans.isPaid
import com.pluk.reader.ui.plans.LectorMark
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.pluk.reader.R
import com.pluk.reader.domain.model.LineSpacing
import com.pluk.reader.domain.model.ReaderSettings
import com.pluk.reader.domain.model.ReadingTheme
import com.pluk.reader.ui.reader.FontChips
import com.pluk.reader.ui.reader.FontControlsPalette
import com.pluk.reader.ui.reader.SizeButtons
import com.pluk.reader.ui.reader.labelFamily
import com.pluk.reader.ui.theme.LightMarginPalette
import com.pluk.reader.ui.theme.MarginColors

/**
 * Colores de cada tema en la vista previa y en su círculo (los del diseño, pantallas 07 y 09). Son los del lector:
 * no cambian con la apariencia de la app (PRF-021).
 */
private fun ReadingTheme.background() = when (this) {
    ReadingTheme.LIGHT -> LightMarginPalette.field
    ReadingTheme.SEPIA -> Color(0xFFF3E9D4)
    ReadingTheme.DARK -> Color(0xFF000000)
}

private fun ReadingTheme.text() = when (this) {
    ReadingTheme.LIGHT -> LightMarginPalette.ink
    ReadingTheme.SEPIA -> Color(0xFF3B2F1E)
    ReadingTheme.DARK -> LightMarginPalette.field
}

private val ReadingTheme.label
    get() = when (this) {
        ReadingTheme.LIGHT -> R.string.profile_theme_light
        ReadingTheme.SEPIA -> R.string.profile_theme_sepia
        ReadingTheme.DARK -> R.string.profile_theme_dark
    }

/** Controles de letra en Perfil: lo elegido en relleno tinta con texto amarillo; sigue la apariencia (PRF-022). */
private val ProfileFontControls: FontControlsPalette
    @Composable get() = FontControlsPalette(
        ink = MarginColors.Ink,
        accent = MarginColors.InkFill,
        onAccent = MarginColors.Yellow,
        line = MarginColors.Off,
    )

/** PRF-011 a PRF-015: vista previa, letra, tema e interlineado. */
@Composable
internal fun ReadingDefaultsCard(reading: ReaderSettings, actions: ProfileActions, onOpenFont: () -> Unit, locked: Boolean = false) {
    ProfileCard {
        Preview(reading)
        ProfileRow(onClick = onOpenFont) {
            Text(stringResource(R.string.profile_font), style = profileText(15, FontWeight.Medium), modifier = Modifier.weight(1f))
            Text(
                stringResource(R.string.profile_font_value, reading.font.familyName, readerFontPoints(reading.fontScale)),
                style = profileText(15, FontWeight.Medium, MarginColors.Ink2).copy(fontFamily = reading.font.labelFamily()),
            )
            RowIcon(ProfileIcons.ChevronRight, MarginColors.Muted)
        }
        CardDivider()
        ProfileRow(onClick = null) {
            Text(stringResource(R.string.profile_theme), style = profileText(15, FontWeight.Medium), modifier = Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf(ReadingTheme.LIGHT, ReadingTheme.SEPIA, ReadingTheme.DARK).forEach { theme ->
                    ThemeSwatch(theme, theme == reading.theme, marked = locked && theme.isPaid) { actions.onTheme(theme) }
                }
            }
        }
        CardDivider()
        ProfileRow(onClick = null) {
            Text(stringResource(R.string.profile_spacing), style = profileText(15, FontWeight.Medium), modifier = Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                SpacingOption(LineSpacing.NORMAL, reading.lineSpacing, actions.onLineSpacing)
                SpacingOption(LineSpacing.WIDE, reading.lineSpacing, actions.onLineSpacing)
            }
        }
    }
}

/** PRF-012: párrafo de muestra con la letra, el tamaño, el tema y el interlineado elegidos. */
@Composable
private fun Preview(reading: ReaderSettings) {
    val size = PREVIEW_SIZE_SP * reading.fontScale
    Text(
        stringResource(R.string.profile_reading_preview),
        style = TextStyle(
            fontFamily = reading.font.labelFamily(),
            fontSize = size.sp,
            lineHeight = reading.lineSpacing.lineHeight.em,
            color = reading.theme.text(),
        ),
        modifier = Modifier
            .padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 4.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(reading.theme.background())
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .testTag("profile-reading-preview"),
    )
}

/** Círculo de 28; el elegido lleva un aro tinta separado por 2 dp, por fuera, sin mover nada. */
@Composable
private fun ThemeSwatch(theme: ReadingTheme, selected: Boolean, marked: Boolean, onClick: () -> Unit) {
    // PLN-015: en Gratis, Sepia y Noche llevan la marca "Lector" debajo del círculo y se anuncian como pagas.
    val name = stringResource(theme.label).let { if (marked) "$it, ${stringResource(R.string.reader_paid_option)}" else it }
    Box(contentAlignment = Alignment.Center) {
    // Se leen antes: la lambda de dibujo no es composable.
    val ring = MarginColors.Ink
    val gap = MarginColors.Field
    Box(
        Modifier
            .size(28.dp)
            .drawBehind {
                if (selected) {
                    val radius = size.minDimension / 2
                    drawCircle(ring, radius + 4.dp.toPx())
                    drawCircle(gap, radius + 2.dp.toPx())
                }
            }
            .clip(CircleShape)
            .background(theme.background())
            .then(if (theme == ReadingTheme.LIGHT) Modifier.border(1.5.dp, MarginColors.Off, CircleShape) else Modifier)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .semantics { contentDescription = name }
            .testTag("profile-theme-${theme.name}"),
    )
    if (marked) LectorMark(Modifier.align(Alignment.BottomCenter).offset(y = 12.dp).clearAndSetSemantics {})
    }
}

/** Botón de 36, radio 10: el elegido en tinta con el ícono amarillo. */
@Composable
private fun SpacingOption(spacing: LineSpacing, selected: LineSpacing, onSelect: (LineSpacing) -> Unit) {
    val isSelected = spacing == selected
    val name = stringResource(if (spacing == LineSpacing.WIDE) R.string.profile_spacing_wide else R.string.profile_spacing_normal)
    Box(
        Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (isSelected) MarginColors.InkFill else Color.Transparent)
            .selectable(selected = isSelected, role = Role.RadioButton) { onSelect(spacing) }
            .semantics { contentDescription = name }
            .testTag("profile-spacing-${spacing.name}"),
        contentAlignment = Alignment.Center,
    ) {
        RowIcon(
            if (spacing == LineSpacing.WIDE) ProfileIcons.DensityLarge else ProfileIcons.DensityMedium,
            if (isSelected) MarginColors.Yellow else MarginColors.InkMuted,
            20.dp,
        )
    }
}

/** PRF-013: panel claro con el tipo de letra y "A−"/"A+", los mismos controles del panel del lector. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FontSheet(reading: ReaderSettings, actions: ProfileActions, onDismiss: () -> Unit, locked: Boolean = false) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MarginColors.Field,
        contentColor = MarginColors.Ink,
        dragHandle = {
            Box(Modifier.padding(top = 10.dp, bottom = 8.dp).width(36.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(MarginColors.Off))
        },
        modifier = Modifier.testTag("profile-font-sheet"),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp).navigationBarsPadding().padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Text(stringResource(R.string.profile_font), style = profileText(15, FontWeight.SemiBold))
            FontChips(reading.font, actions.onFont, ProfileFontControls, locked = locked)
            SizeButtons(actions.onSmallerFont, actions.onBiggerFont, ProfileFontControls)
        }
    }
}

private const val PREVIEW_SIZE_SP = 16.0
