package com.pluk.reader.ui.welcome

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.pluk.reader.R
import com.pluk.reader.ui.theme.HostGrotesk
import com.pluk.reader.ui.theme.MarginColors
import com.pluk.reader.ui.theme.ReaderTheme

/** Texto secundario sobre el amarillo (diseño 01). */
private val InkSoft = Color(0xFF3A3115)

/** En tablet el contenido no se estira: columna de este ancho como máximo (WEL-007). */
private val MaxContentWidth = 560.dp

/**
 * Bienvenida, diseño "01 — Welcome" (WEL-004). "Comenzar" (el botón o la flecha) llama a [onStart] (WEL-005).
 * Si no entra en la pantalla (apaisado), se desplaza (WEL-007).
 */
@Composable
fun WelcomeScreen(onStart: () -> Unit, modifier: Modifier = Modifier) {
    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .background(MarginColors.Yellow)
            .systemBarsPadding(),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            Modifier
                .widthIn(max = MaxContentWidth)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .heightIn(min = maxHeight),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Brand(Modifier.padding(start = 28.dp, end = 28.dp, top = 28.dp))
                BasicText(
                    text = stringResource(R.string.welcome_headline),
                    style = TextStyle(
                        color = MarginColors.Ink,
                        fontFamily = HostGrotesk,
                        fontWeight = FontWeight.Normal,
                        fontSize = 54.sp,
                        lineHeight = 1.02.em,
                        letterSpacing = (-0.045).em,
                    ),
                    // "Scrolleá menos." no entra a 54 sp en un teléfono angosto: se achica antes de cortar la línea.
                    maxLines = 3,
                    autoSize = TextAutoSize.StepBased(minFontSize = 32.sp, maxFontSize = 54.sp),
                    modifier = Modifier
                        .padding(start = 28.dp, end = 28.dp, top = 64.dp)
                        .semantics { heading() }
                        .testTag("welcome-headline"),
                )
                Text(
                    text = stringResource(R.string.welcome_body),
                    color = InkSoft,
                    fontSize = 15.sp,
                    lineHeight = 1.45.em,
                    modifier = Modifier
                        .padding(start = 28.dp, end = 28.dp, top = 24.dp)
                        .widthIn(max = 250.dp),
                )
            }
            Column(Modifier.padding(top = 40.dp)) {
                StartButton(onStart, Modifier.padding(horizontal = 28.dp))
                Text(
                    text = stringResource(R.string.welcome_footer),
                    color = InkSoft,
                    fontSize = 11.sp,
                    modifier = Modifier
                        .align(Alignment.End)
                        .padding(start = 28.dp, end = 28.dp, top = 28.dp, bottom = 14.dp),
                )
            }
        }
    }
}

/** Logo y "margin.", anunciados juntos como "margin." (WEL-007). */
@Composable
private fun Brand(modifier: Modifier = Modifier) {
    val name = stringResource(R.string.welcome_brand)
    Row(
        modifier.clearAndSetSemantics { contentDescription = name },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = painterResource(R.drawable.ic_margin_logo),
            contentDescription = null,
            modifier = Modifier.size(width = 30.dp, height = 24.dp),
        )
        Text(
            text = name,
            color = MarginColors.Ink,
            fontSize = 20.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = (-0.03).em,
            modifier = Modifier.padding(start = 10.dp),
        )
    }
}

/** Píldora "Comenzar" y círculo con la flecha: para TalkBack son un solo botón "Comenzar" (WEL-007). */
@Composable
private fun StartButton(onStart: () -> Unit, modifier: Modifier = Modifier) {
    val label = stringResource(R.string.welcome_start)
    val stroke = BorderStroke(1.5.dp, MarginColors.Ink)
    Row(
        modifier
            .testTag("welcome-start")
            .clearAndSetSemantics {
                contentDescription = label
                role = Role.Button
                onClick { onStart(); true }
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .height(48.dp)
                .border(stroke, RoundedCornerShape(24.dp))
                .clickable(onClick = onStart)
                .padding(horizontal = 30.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(label, color = MarginColors.Ink, fontSize = 16.sp)
        }
        Box(
            Modifier
                .padding(start = 10.dp)
                .size(48.dp)
                .border(stroke, CircleShape)
                .clickable(onClick = onStart),
            contentAlignment = Alignment.Center,
        ) {
            Icon(ArrowForward, contentDescription = null, tint = MarginColors.Ink, modifier = Modifier.size(22.dp))
        }
    }
}

/** Flecha fina como `arrow_forward` de Material Symbols Rounded en peso 300 (diseño 01). */
private val ArrowForward: ImageVector = ImageVector.Builder("ArrowForward", 24.dp, 24.dp, 24f, 24f).apply {
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 1.5f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
    ) {
        moveTo(4.5f, 12f)
        horizontalLineTo(19f)
        moveTo(13f, 6f)
        lineTo(19f, 12f)
        lineTo(13f, 18f)
    }
}.build()

@Preview(widthDp = 360, heightDp = 780)
@Composable
private fun WelcomeScreenPreview() {
    ReaderTheme { WelcomeScreen(onStart = {}) }
}
