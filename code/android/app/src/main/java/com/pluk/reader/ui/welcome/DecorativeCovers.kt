package com.pluk.reader.ui.welcome

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/**
 * Portadas de ilustración del diseño (L0, banner de Inicio). No son libros: no se tocan ni se anuncian (GST-019,
 * GST-023). Colores fijos, como el amarillo de la marca: se ven igual en modo claro y oscuro.
 */
internal object CoverColors {
    val Ink = Color(0xFF130000)
    val Yellow = Color(0xFFFBD256)
    val Sage = Color(0xFF7A8F6A)
    val Navy = Color(0xFF1F3A5F)
    val Bone = Color(0xFFE8E2D3)
    val Paper = Color(0xFFFDFDFD)
}

@Composable
private fun rememberSerif(): FontFamily {
    val assets = LocalContext.current.assets
    return remember { FontFamily(Font("fonts/Newsreader.ttf", assets)) }
}

/** Una portada con sombra, inclinada [degrees] y desplazada a ([x], [y]) dentro de su caja. */
@Composable
private fun Cover(
    x: Dp,
    y: Dp,
    width: Dp,
    height: Dp,
    degrees: Float,
    color: Color,
    corner: Dp,
    elevation: Dp,
    content: @Composable () -> Unit = {},
) {
    Box(
        Modifier
            .offset(x, y)
            .size(width, height)
            .rotate(degrees)
            .shadow(elevation, RoundedCornerShape(corner))
            .background(color, RoundedCornerShape(corner)),
    ) { content() }
}

private fun coverTitle(size: TextUnit, color: Color) = TextStyle(
    color = color,
    fontSize = size,
    fontWeight = FontWeight.ExtraBold,
    lineHeight = 0.88.em,
    letterSpacing = (-0.05).em,
)

/** L0 (GST-019): tres portadas grandes, la del medio adelante. */
@Composable
fun ReadyCovers(modifier: Modifier = Modifier) {
    val serif = rememberSerif()
    Box(modifier.width(302.dp).height(250.dp).clearAndSetSemantics { }) {
        Cover(4.dp, 28.dp, 118.dp, 176.dp, -8f, CoverColors.Sage, 8.dp, 12.dp) {
            Text(
                "Letters from a Stoic",
                style = TextStyle(color = CoverColors.Paper, fontFamily = serif, fontSize = 17.sp, lineHeight = 1.em),
                modifier = Modifier.padding(10.dp),
            )
        }
        Cover(180.dp, 28.dp, 118.dp, 176.dp, 8f, CoverColors.Bone, 8.dp, 12.dp) {
            Text(
                "The\nOdyssey",
                style = TextStyle(color = CoverColors.Ink, fontFamily = serif, fontSize = 18.sp, fontWeight = FontWeight.Medium, lineHeight = 1.em),
                modifier = Modifier.padding(10.dp),
            )
        }
        Cover(88.dp, 6.dp, 130.dp, 194.dp, 0f, CoverColors.Ink, 8.dp, 16.dp) {
            Column(Modifier.padding(12.dp).fillMaxWidth(), verticalArrangement = Arrangement.SpaceBetween) {
                Text("MEDI-\nTATI-\nONS", style = coverTitle(24.sp, CoverColors.Yellow))
                Box(Modifier.weight(1f))
                Text(
                    "MARCUS AURELIUS",
                    style = TextStyle(color = CoverColors.Paper, fontSize = 8.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.08.em),
                )
            }
        }
    }
}

/** Banner de Inicio (GST-023): tres portadas chicas y una insignia amarilla con [badge] abajo a la derecha. */
@Composable
fun BannerCovers(badge: @Composable () -> Unit, borderColor: Color, modifier: Modifier = Modifier) {
    Box(modifier.size(84.dp, 96.dp).clearAndSetSemantics { }) {
        Cover(0.dp, 14.dp, 44.dp, 66.dp, -10f, CoverColors.Navy, 4.dp, 6.dp)
        Cover(38.dp, 16.dp, 44.dp, 66.dp, 9f, CoverColors.Sage, 4.dp, 6.dp)
        Cover(18.dp, 6.dp, 48.dp, 72.dp, 0f, CoverColors.Ink, 4.dp, 8.dp) {
            Text("MEDI-\nTATI-\nONS", style = coverTitle(9.sp, CoverColors.Yellow), modifier = Modifier.padding(5.dp))
        }
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .offset(x = 2.dp)
                .size(30.dp)
                .background(borderColor, RoundedCornerShape(15.dp))
                .padding(2.5.dp)
                .background(CoverColors.Yellow, RoundedCornerShape(13.dp)),
            contentAlignment = Alignment.Center,
        ) { badge() }
    }
}
