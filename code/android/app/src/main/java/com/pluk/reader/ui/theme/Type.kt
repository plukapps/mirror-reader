package com.pluk.reader.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import com.pluk.reader.R

// Host Grotesk (OFL), fuentes variables con eje wght 300-800. minSdk 26 soporta variaciones.
private val appWeights =
  listOf(
    FontWeight.Light,
    FontWeight.Normal,
    FontWeight.Medium,
    FontWeight.SemiBold,
    FontWeight.Bold,
    FontWeight.ExtraBold,
  )

@OptIn(ExperimentalTextApi::class)
val HostGrotesk =
  FontFamily(
    appWeights.flatMap { weight ->
      listOf(
        Font(
          resId = R.font.host_grotesk,
          weight = weight,
          style = FontStyle.Normal,
          variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
        ),
        Font(
          resId = R.font.host_grotesk_italic,
          weight = weight,
          style = FontStyle.Italic,
          variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
        ),
      )
    }
  )

private val base = Typography()

/**
 * K-160: interlineado en `em` (proporcional al tamaño de letra) en vez de `sp` fijo. Así un `Text` que solo cambia
 * `fontSize` no hereda las líneas del estilo base: con 24 sp fijos, un texto de 11 sp ocupaba líneas de 24 sp.
 * `Trim.Both` quita el aire que la fuente deja arriba de la primera línea y debajo de la última.
 * Criterio: lo más apretado posible, interlineado igual al tamaño de letra. Si un rol necesita más aire, se le pasa
 * su propio `ratio` en [Typography].
 */
private val compactLineHeight =
  LineHeightStyle(alignment = LineHeightStyle.Alignment.Center, trim = LineHeightStyle.Trim.Both)

private const val DEFAULT_LINE_HEIGHT = 1f

/** Host Grotesk con interlineado [ratio] veces el tamaño de letra. */
private fun TextStyle.app(ratio: Float = DEFAULT_LINE_HEIGHT) =
  copy(fontFamily = HostGrotesk, lineHeight = ratio.em, lineHeightStyle = compactLineHeight)

val Typography =
  Typography(
    displayLarge = base.displayLarge.app(),
    displayMedium = base.displayMedium.app(),
    displaySmall = base.displaySmall.app(),
    headlineLarge = base.headlineLarge.app(),
    headlineMedium = base.headlineMedium.app(),
    headlineSmall = base.headlineSmall.app(),
    titleLarge = base.titleLarge.app(),
    titleMedium = base.titleMedium.app(),
    titleSmall = base.titleSmall.app(),
    // Estilo por defecto de todo `Text` sin `style` (MaterialTheme lo provee como LocalTextStyle).
    bodyLarge = base.bodyLarge.app(),
    bodyMedium = base.bodyMedium.app(),
    bodySmall = base.bodySmall.app(),
    labelLarge = base.labelLarge.app(),
    labelMedium = base.labelMedium.app(),
    labelSmall = base.labelSmall.app(),
  )
