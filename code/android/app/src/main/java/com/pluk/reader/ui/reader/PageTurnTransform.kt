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
 * a la derecha. El aclarado del fondo siempre es de la capa que va encima.
 * Es matemática pura para poder probarla sin Android.
 */
data class PageTurnTransform(
    val outgoingTranslationX: Float,
    /** Cuánto del aclarado del fondo (0..1) se aplica a la página que sale. Crece despacio y se nota más al final. */
    val outgoingLightenAmount: Float,
    val incomingTranslationX: Float,
    /** Cuánto del aclarado del fondo (0..1) se aplica a la página que entra. Solo al retroceder. */
    val incomingLightenAmount: Float,
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
                    incomingLightenAmount = 0f,
                )
                // Rollback: el avance en el instante 1 - p.
                PageTurnDirection.Backward -> PageTurnTransform(
                    outgoingTranslationX = p * PARALLAX_FRACTION * pageWidth,
                    outgoingLightenAmount = 0f,
                    incomingTranslationX = -(1f - p) * pageWidth,
                    incomingLightenAmount = topLighten(1f - p),
                )
            }
        }


        /** Cuadrática: el aclarado es gradual y se nota más hacia el final del recorrido que al comienzo. */
        private fun topLighten(p: Float) = p * p
    }
}
