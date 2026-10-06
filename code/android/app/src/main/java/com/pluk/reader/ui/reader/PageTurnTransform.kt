package com.pluk.reader.ui.reader

enum class PageTurnDirection { Forward, Backward }

/**
 * Transformaciones de la animación "deslizar con paralaje" (RDR-009), en función del progreso.
 *
 * Avanzando, la página actual (una captura) sale hacia la izquierda bajando un poco su opacidad,
 * y la nueva, que está debajo, entra desde un poco a la derecha de su sitio. Retroceder es el espejo.
 * Es matemática pura para poder probarla sin Android.
 */
data class PageTurnTransform(
    val outgoingTranslationX: Float,
    val outgoingAlpha: Float,
    val incomingTranslationX: Float,
    /** Cuánto del aclarado del fondo (0..1) se aplica a la página que sale. Crece rápido para no ver un salto. */
    val outgoingLightenAmount: Float,
) {
    companion object {
        /** Cuánto de su ancho se desplaza la página nueva al empezar. */
        const val PARALLAX_FRACTION = 0.2f

        /** Opacidad de la página que sale al terminar. */
        const val OUTGOING_END_ALPHA = 0.7f

        /** Avance con el que el fondo de la página que sale ya está del todo aclarado. */
        const val LIGHTEN_RAMP = 0.1f

        fun at(progress: Float, direction: PageTurnDirection, pageWidth: Float): PageTurnTransform {
            val p = progress.coerceIn(0f, 1f)
            val outgoingSign = if (direction == PageTurnDirection.Forward) -1f else 1f
            return PageTurnTransform(
                outgoingTranslationX = outgoingSign * p * pageWidth,
                outgoingAlpha = 1f - (1f - OUTGOING_END_ALPHA) * p,
                incomingTranslationX = -outgoingSign * (1f - p) * PARALLAX_FRACTION * pageWidth,
                outgoingLightenAmount = (p / LIGHTEN_RAMP).coerceIn(0f, 1f),
            )
        }
    }
}
