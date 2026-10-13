package com.pluk.reader.ui.theme

import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect

/**
 * PRF-021, K-200: íconos de la barra de estado y de navegación que se leen sobre el fondo: oscuros si
 * [darkIcons] (fondo claro), claros si no. Volver a llamar `enableEdgeToEdge` solo cambia los estilos.
 */
@Composable
fun SystemBarsEffect(activity: ComponentActivity, darkIcons: Boolean) {
    LaunchedEffect(activity, darkIcons) {
        activity.enableEdgeToEdge(
            statusBarStyle = if (darkIcons) SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
            else SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = if (darkIcons) SystemBarStyle.light(LIGHT_SCRIM, DARK_SCRIM)
            else SystemBarStyle.dark(DARK_SCRIM),
        )
    }
}

// Los mismos velos que usa `enableEdgeToEdge` por defecto para la navegación con tres botones.
private val LIGHT_SCRIM = Color.argb(0xe6, 0xFF, 0xFF, 0xFF)
private val DARK_SCRIM = Color.argb(0x80, 0x1b, 0x1b, 0x1b)
