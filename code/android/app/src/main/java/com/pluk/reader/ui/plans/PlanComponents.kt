package com.pluk.reader.ui.plans

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.pluk.reader.ui.theme.MarginColors

/*
 * Piezas compartidas de Planes (P01, P02, P03, aviso del 80 %, P04). Medidas del diseño `Margin Planes.dc.html`:
 * px = dp/sp. Sin espaciado entre letras salvo donde se indica.
 */

/** Host Grotesk del tema, sin espaciado extra salvo [letterSpacing]. */
@Composable
internal fun planText(
    size: Number,
    weight: FontWeight = FontWeight.Normal,
    color: Color = MarginColors.Ink,
    letterSpacing: TextUnit = 0.em,
): TextStyle = LocalTextStyle.current.copy(
    fontSize = size.toFloat().sp,
    fontWeight = weight,
    color = color,
    letterSpacing = letterSpacing,
)

/** JetBrains Mono de los assets del lector (ADR 0009): proporciones y tamaños. */
@Composable
internal fun rememberMonoFamily(): FontFamily {
    val assets = LocalContext.current.assets
    return remember { FontFamily(Font("fonts/JetBrainsMono.ttf", assets, FontWeight.Medium)) }
}

/** Newsreader en itálica de los assets del lector (ADR 0009): "Si cancelás, no perdés nada…". */
@Composable
internal fun rememberSerifItalicFamily(): FontFamily {
    val assets = LocalContext.current.assets
    return remember { FontFamily(Font("fonts/Newsreader-Italic.ttf", assets, FontWeight.Normal, FontStyle.Italic)) }
}

/** Barra de uso: 6 de alto, radio 3, pista [MarginColors.Off] y relleno tinta. */
@Composable
internal fun UsageBar(fraction: Float, modifier: Modifier = Modifier, track: Color = MarginColors.Off, fill: Color = MarginColors.Ink) {
    Box(
        modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(track),
    ) {
        if (fraction > 0f) {
            Box(
                Modifier
                    .fillMaxWidth(fraction)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(3.dp))
                    .background(fill),
            )
        }
    }
}

/** Marca "Tu plan": borde tinta de 1,5, con o sin tilde. */
@Composable
internal fun YourPlanChip(text: String, large: Boolean = false, withCheck: Boolean = true, color: Color = MarginColors.Ink) {
    val height = if (large) 30.dp else 28.dp
    Row(
        Modifier
            .height(height)
            .border(1.5.dp, color, RoundedCornerShape(height / 2))
            .padding(horizontal = if (large) 12.dp else 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (withCheck) Icon(PlanIcons.Check, null, tint = color, modifier = Modifier.size(16.dp))
        Text(text, style = planText(if (large) 13 else 12, FontWeight.Bold, color))
    }
}

/** Botón principal: 56 de alto, radio 28, tinta con texto amarillo, flecha opcional (P02, P03, P04). */
@Composable
internal fun PrimaryPlanButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
) {
    Row(
        modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(MarginColors.InkFill)
            .clickable(role = Role.Button, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    ) {
        Text(text, style = planText(16, FontWeight.Bold, MarginColors.Yellow))
        if (icon != null) Icon(icon, null, tint = MarginColors.Yellow, modifier = Modifier.size(20.dp))
    }
}

/** Botón secundario sin fondo: "Ahora no". */
@Composable
internal fun TextPlanButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, height: Dp = 52.dp) {
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(height / 2))
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = planText(15, FontWeight.SemiBold))
    }
}

/** Cuadro amarillo de 52 con radio 16 y el ícono de la hoja (P02a, P03b). */
@Composable
internal fun SheetIcon(icon: ImageVector) {
    Box(
        Modifier
            .size(52.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MarginColors.Yellow),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = MarginColors.OnYellow, modifier = Modifier.size(28.dp))
    }
}

/** Columna de texto con separación: título de hoja de 32 y bajada de 15 (P02, P03). */
@Composable
internal fun SheetTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = planText(32, color = MarginColors.Ink, letterSpacing = (-0.04).em).copy(lineHeight = 1.04.em),
        modifier = modifier,
    )
}

@Composable
internal fun SheetBody(text: androidx.compose.ui.text.AnnotatedString, modifier: Modifier = Modifier) {
    Text(text, style = planText(15, color = MarginColors.Ink2).copy(lineHeight = 1.45.em), modifier = modifier)
}

/** Botones de pie de las hojas: "Ver planes" con flecha y "Ahora no" (P02, P03). */
@Composable
internal fun SheetActions(seePlans: String, notNow: String, onSeePlans: () -> Unit, onNotNow: () -> Unit) {
    Column(Modifier.padding(top = 20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        PrimaryPlanButton(seePlans, onSeePlans, icon = PlanIcons.ArrowForward)
        TextPlanButton(notNow, onNotNow)
    }
}

/** PLN-015: marca "Plus" de las opciones pagas (temas y fuentes). Amarilla con texto tinta, igual en los dos modos. */
@Composable
fun LectorMark(modifier: Modifier = Modifier) {
    Text(
        androidx.compose.ui.res.stringResource(com.pluk.reader.R.string.plans_plus),
        style = planText(9, FontWeight.Bold, MarginColors.OnYellow),
        maxLines = 1,
        modifier = modifier
            .clip(RoundedCornerShape(99.dp))
            .background(MarginColors.Yellow)
            .padding(horizontal = 5.dp, vertical = 2.dp),
    )
}
