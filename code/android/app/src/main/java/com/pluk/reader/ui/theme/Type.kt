package com.pluk.reader.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
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

val Typography =
  Typography(
    displayLarge = base.displayLarge.copy(fontFamily = HostGrotesk),
    displayMedium = base.displayMedium.copy(fontFamily = HostGrotesk),
    displaySmall = base.displaySmall.copy(fontFamily = HostGrotesk),
    headlineLarge = base.headlineLarge.copy(fontFamily = HostGrotesk),
    headlineMedium = base.headlineMedium.copy(fontFamily = HostGrotesk),
    headlineSmall = base.headlineSmall.copy(fontFamily = HostGrotesk),
    titleLarge = base.titleLarge.copy(fontFamily = HostGrotesk),
    titleMedium = base.titleMedium.copy(fontFamily = HostGrotesk),
    titleSmall = base.titleSmall.copy(fontFamily = HostGrotesk),
    bodyLarge =
      TextStyle(
        fontFamily = HostGrotesk,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp,
      ),
    bodyMedium = base.bodyMedium.copy(fontFamily = HostGrotesk),
    bodySmall = base.bodySmall.copy(fontFamily = HostGrotesk),
    labelLarge = base.labelLarge.copy(fontFamily = HostGrotesk),
    labelMedium = base.labelMedium.copy(fontFamily = HostGrotesk),
    labelSmall = base.labelSmall.copy(fontFamily = HostGrotesk),
  )
