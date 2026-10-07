package com.pluk.reader.ui.reader

enum class PageTurnDirection { Forward, Backward }

/**
 * Transformaciones de la animación "deslizar con paralaje" (RDR-009), en función del progreso.
 *
 * Avanzando, la página actual (una captura, la "saliente") va encima y sale hacia la izquierda,
 * y la nueva (la "entrante", el navegador en vivo) está debajo y entra desde un poco a la derecha.
 *
 * Retroceder es esa misma animación reproducida al revés: la entrante (la página anterior) pasa a ir encima y
 * entra desde la izquierda, y la saliente (la captura) queda debajo y se desplaza un poco
 * a la derecha. El aclarado del fondo es siempre de la página que sale del foco, vaya encima o debajo.
 * Es matemática pura para poder probarla sin Android.
 */
data class PageTurnTransform(
    val outgoingTranslationX: Float,
    /** Cuánto del aclarado del fondo (0..1) se aplica a la página que sale. Crece desde el comienzo y llega al máximo al 80% del recorrido. */
    val outgoingLightenAmount: Float,
    val incomingTranslationX: Float,
) {
    companion object {
        /** Cuánto de su ancho se desplaza la página de abajo. */
        const val PARALLAX_FRACTION = 0.2f

        fun at(progress: Float, direction: PageTurnDirection, pageWidth: Float): PageTurnTransform {
            val p = progress.coerceIn(0f, 1f)
            return when (direction) {
                PageTurnDirection.Forward -> PageTurnTransform(
                    outgoingTranslationX = -p * pageWidth,
                    outgoingLightenAmount = topLighten(p),
                    incomingTranslationX = (1f - p) * PARALLAX_FRACTION * pageWidth,
                )
                // Rollback del movimiento: la que entra repite al revés el avance. El aclarado, en cambio, sigue
                // la misma fórmula que al avanzar, pero sobre la página que se estaba viendo.
                PageTurnDirection.Backward -> PageTurnTransform(
                    outgoingTranslationX = p * PARALLAX_FRACTION * pageWidth,
                    outgoingLightenAmount = topLighten(p),
                    incomingTranslationX = -(1f - p) * pageWidth,
                )
            }
        }


        /** Avance con el que el aclarado del fondo ya llegó al máximo. */
        const val LIGHTEN_END = 0.8f

        /** Crece sin saltos desde 0 al comienzo hasta 1 al 80% del recorrido, y ahí se queda. */
        private fun topLighten(p: Float) = (p / LIGHTEN_END).coerceIn(0f, 1f)
    }
}
