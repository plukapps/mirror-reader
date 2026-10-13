package com.pluk.reader.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Colores que cambian entre el modo claro y el oscuro (PRF-021, PRF-022). Los valores siguen la tabla de
 * `specs/plans/2026-10-13-profile-settings.md`; la paleta oscura sale del panel de ajustes y de la tarjeta de ayuda.
 */
@Immutable
data class MarginPalette(
    /** Fondo de pantalla. */
    val paper: Color,
    /** Tarjetas y campo de búsqueda. */
    val field: Color,
    /** Texto e íconos principales. */
    val ink: Color,
    /** Texto e íconos secundarios oscuros: candado, valores de fila, segmentos sin elegir (09). */
    val ink2: Color,
    /** Títulos de sección y detalles. */
    val muted: Color,
    /** Bordes. */
    val line: Color,
    /** Separadores de tarjeta y pista de la barra de espacio (09). */
    val hairline: Color,
    /** Interruptor apagado y borde del círculo del tema Clásico (09). */
    val off: Color,
    /** Rellenos oscuros: barra inferior, elegidos, "GRATIS", tarjeta de ayuda. */
    val inkFill: Color,
    /** Separadores sobre [inkFill]. */
    val inkLine: Color,
    val isDark: Boolean,
)

val LightMarginPalette = MarginPalette(
    paper = Color(0xFFF1F1F1),
    field = Color(0xFFFDFDFD),
    ink = Color(0xFF130000),
    ink2 = Color(0xFF3A3830),
    muted = Color(0xFF6B6650),
    line = Color(0xFFDDD9CE),
    hairline = Color(0xFFE7E4DC),
    off = Color(0xFFDAD6CA),
    inkFill = Color(0xFF130000),
    inkLine = Color(0xFF33322D),
    isDark = false,
)

val DarkMarginPalette = MarginPalette(
    paper = Color(0xFF130000),
    field = Color(0xFF221C19),
    ink = Color(0xFFFDFDFD),
    ink2 = Color(0xFFCFCAC0),
    muted = Color(0xFFA8A496),
    line = Color(0xFF4A4840),
    hairline = Color(0xFF33322D),
    off = Color(0xFF4A4840),
    inkFill = Color(0xFF2B2421),
    inkLine = Color(0xFF3D3530),
    isDark = true,
)

/** Paleta vigente. La raíz la provee según la apariencia (PRF-021); onboarding y lector fuerzan la clara. */
val LocalMarginPalette = staticCompositionLocalOf { LightMarginPalette }

/** PRF-021: onboarding (O1 a O9) siempre como su diseño, en claro. */
@Composable
fun LightPalette(content: @Composable () -> Unit) =
    CompositionLocalProvider(LocalMarginPalette provides LightMarginPalette, content = content)

/**
 * Paleta "Editorial bold" del diseño (`design/Margin Ebook App.dc.html`). Los colores que dependen del modo se leen
 * de [LocalMarginPalette] (por eso son `@Composable`); fuera de composición, leerlos antes y pasar el valor.
 * Los que van sobre amarillo, el amarillo, el rojo y las portadas no cambian (PRF-022).
 */
object MarginColors {
    val Ink: Color @Composable @ReadOnlyComposable get() = LocalMarginPalette.current.ink
    val Paper: Color @Composable @ReadOnlyComposable get() = LocalMarginPalette.current.paper
    val Muted: Color @Composable @ReadOnlyComposable get() = LocalMarginPalette.current.muted
    val Line: Color @Composable @ReadOnlyComposable get() = LocalMarginPalette.current.line

    /** Fondo de tarjetas y del campo de búsqueda. */
    val Field: Color @Composable @ReadOnlyComposable get() = LocalMarginPalette.current.field

    /** Separadores de tarjeta y pista de la barra de espacio (diseño 09 Profile). */
    val Hairline: Color @Composable @ReadOnlyComposable get() = LocalMarginPalette.current.hairline

    /** Texto e íconos secundarios oscuros: candado, valores de fila, segmentos sin elegir (09). */
    val Ink2: Color @Composable @ReadOnlyComposable get() = LocalMarginPalette.current.ink2

    /** Interruptor apagado y borde del círculo del tema Clásico (09). */
    val Off: Color @Composable @ReadOnlyComposable get() = LocalMarginPalette.current.off

    /** Rellenos oscuros (no texto): barra inferior, elegidos, "GRATIS", tarjeta de ayuda, avisos. */
    val InkFill: Color @Composable @ReadOnlyComposable get() = LocalMarginPalette.current.inkFill

    /** Tarjeta blanca de Inicio ("Nada en lectura"): blanco en claro, [Field] en oscuro. */
    val Card: Color @Composable @ReadOnlyComposable get() =
        LocalMarginPalette.current.let { if (it.isDark) it.field else Color.White }

    /** Texto secundario sobre [InkFill]: el autor en "Seguir leyendo". */
    val OnInkFillMuted = Color(0xFFDDD9CE)

    /** Separadores dentro de un relleno [InkFill]: la tarjeta de ayuda (09). */
    val InkLine: Color @Composable @ReadOnlyComposable get() = LocalMarginPalette.current.inkLine

    val Yellow = Color(0xFFFBD256)

    /** Texto e íconos sobre amarillo: no cambian con el modo (PRF-022). */
    val OnYellow = Color(0xFF130000)

    /** Texto secundario sobre amarillo: el email de la cabecera (09). */
    val OnYellowMuted = Color(0xFF3A3115)

    /** Texto claro sobre [InkFill], igual en los dos modos. */
    val OnInkFill = Color(0xFFFDFDFD)

    /** Íconos inactivos sobre [InkFill] (barra inferior). */
    val InkMuted = Color(0xFF8A8676)

    val CoverBorder = Color(0xFFFFFFFF)

    /** Punto de "leyendo" en los resultados de búsqueda. */
    val ReadingDot = Color(0xFFC9A100)

    /** Acción destructiva: "Quitar de la biblioteca" (diseño 05b). */
    val Danger = Color(0xFFB3261E)

    /** Fondos de las portadas generadas cuando el EPUB no trae una. */
    val Covers = listOf(
        Color(0xFF130000), Color(0xFF1F3A5F), Color(0xFFFBD256), Color(0xFFE45A3F),
        Color(0xFF8B1E1E), Color(0xFF7A8F6A), Color(0xFF2F2E29), Color(0xFFE8E2D3),
    )
}
