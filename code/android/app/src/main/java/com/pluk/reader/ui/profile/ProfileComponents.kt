package com.pluk.reader.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.pluk.reader.ui.theme.MarginColors

/*
 * Piezas del Perfil (diseño "09 — Profile"). Las medidas salen del HTML: px = dp/sp.
 * El diseño no lleva espaciado entre letras salvo donde se indica; se anula el del estilo base.
 */

/** Estilo de texto del Perfil: Host Grotesk del tema, sin espaciado extra salvo [letterSpacing]. */
@Composable
internal fun profileText(
    size: Int,
    weight: FontWeight = FontWeight.Normal,
    color: Color = MarginColors.Ink,
    letterSpacing: TextUnit = 0.em,
): TextStyle = LocalTextStyle.current.copy(
    fontSize = size.sp,
    fontWeight = weight,
    color = color,
    letterSpacing = letterSpacing,
)

/** Título de sección: 11 semibold, mayúsculas, espaciado 0,08 em. */
@Composable
internal fun SectionTitle(text: String, bottom: Dp = 10.dp) {
    Text(
        text = text.uppercase(),
        style = profileText(11, FontWeight.SemiBold, MarginColors.Muted, 0.08.em),
        modifier = Modifier
            .padding(start = 24.dp, end = 24.dp, top = 28.dp, bottom = bottom)
            .semantics { heading() },
    )
}

/** Tarjeta de sección: margen 16 a los lados, radio 20. */
@Composable
internal fun ProfileCard(
    background: Color = MarginColors.Field,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(background),
        content = content,
    )
}

/** Separador de 1 dp a lo ancho de la tarjeta. */
@Composable
internal fun CardDivider(color: Color = MarginColors.Hairline) {
    HorizontalDivider(thickness = 1.dp, color = color)
}

/** Fila de tarjeta: 56 de alto mínimo, relleno 16 a los lados, separación 12. */
@Composable
internal fun ProfileRow(
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    minHeight: Dp = 56.dp,
    role: Role = Role.Button,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = minHeight)
            .then(if (onClick != null) Modifier.clickable(role = role, onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

@Composable
internal fun RowIcon(icon: ImageVector, tint: Color, size: Dp = 22.dp) {
    Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(size))
}

/** Fila con texto, ícono opcional y flecha: "Cambiar contraseña". */
@Composable
internal fun NavigationRow(
    text: String,
    onClick: () -> Unit,
    icon: ImageVector? = null,
    iconTint: Color = MarginColors.Ink2,
) {
    ProfileRow(onClick) {
        if (icon != null) RowIcon(icon, iconTint)
        Text(text, style = profileText(15, FontWeight.Medium), modifier = Modifier.weight(1f))
        RowIcon(ProfileIcons.ChevronRight, MarginColors.Muted)
    }
}

/** Fila de acción sin flecha: "Cerrar sesión", "Eliminar cuenta". */
@Composable
internal fun ActionRow(text: String, icon: ImageVector, color: Color, onClick: () -> Unit) {
    ProfileRow(onClick) {
        RowIcon(icon, color)
        Text(text, style = profileText(15, FontWeight.SemiBold, color), modifier = Modifier.weight(1f))
    }
}

/** Etiqueta del plan: alto 22, radio 11, tinta con texto amarillo. */
@Composable
internal fun PlanChip(text: String) {
    Box(
        Modifier
            .height(22.dp)
            .clip(RoundedCornerShape(11.dp))
            .background(MarginColors.InkFill)
            .padding(horizontal = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = profileText(11, FontWeight.SemiBold, MarginColors.Yellow, 0.04.em))
    }
}

/** Barra de espacio: 8 de alto, radio 4, pista clara y relleno tinta. */
@Composable
internal fun StorageBar(fraction: Float) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(MarginColors.Hairline),
    ) {
        if (fraction > 0f) {
            Box(
                Modifier
                    .fillMaxWidth(fraction)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(4.dp))
                    // Tinta (no relleno): en oscuro, una barra clara que se ve sobre la pista.
                    .background(MarginColors.Ink),
            )
        }
    }
}

/** Cuadro de 36 con radio 10 y fondo papel para el ícono del dispositivo. */
@Composable
internal fun DeviceIcon(icon: ImageVector) {
    Box(
        Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(MarginColors.Paper),
        contentAlignment = Alignment.Center,
    ) {
        RowIcon(icon, MarginColors.Ink, 20.dp)
    }
}

/** Interruptor del diseño: 46×28, perilla de 20 a 4 del borde. Solo dibuja; el estado lo anuncia la fila. */
@Composable
internal fun ProfileSwitch(checked: Boolean) {
    Box(
        Modifier
            .size(width = 46.dp, height = 28.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (checked) MarginColors.InkFill else MarginColors.Off)
            .padding(4.dp),
        contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(if (checked) MarginColors.Yellow else MarginColors.Field),
        )
    }
}

/** Opción de un control segmentado. */
internal data class Segment(val label: String, val icon: ImageVector)

/** Control segmentado: pista papel radio 22, relleno 4, segmentos de 36 de alto y radio 18. */
@Composable
internal fun SegmentedControl(segments: List<Segment>, selected: Int, onSelect: (Int) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(MarginColors.Paper)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        segments.forEachIndexed { index, segment ->
            val isSelected = index == selected
            Row(
                Modifier
                    .weight(1f)
                    .height(36.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(if (isSelected) MarginColors.InkFill else Color.Transparent)
                    .clickable(role = Role.RadioButton) { onSelect(index) }
                    .semantics { this.selected = isSelected },
                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val color = if (isSelected) MarginColors.Yellow else MarginColors.Ink2
                RowIcon(segment.icon, color, 18.dp)
                Text(
                    segment.label,
                    style = profileText(13, if (isSelected) FontWeight.SemiBold else FontWeight.Medium, color),
                )
            }
        }
    }
}

/** Círculo de la cabecera con la inicial: 56, tinta, letra 22 bold amarilla. */
@Composable
internal fun Avatar(initial: String) {
    Box(
        Modifier
            .size(56.dp)
            .clip(CircleShape)
            // Va sobre la cabecera amarilla: tinta fija en los dos modos (PRF-022).
            .background(MarginColors.OnYellow),
        contentAlignment = Alignment.Center,
    ) {
        Text(initial, style = profileText(22, FontWeight.Bold, MarginColors.Yellow))
    }
}
