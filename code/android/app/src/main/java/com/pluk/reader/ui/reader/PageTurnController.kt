package com.pluk.reader.ui.reader

import android.graphics.Bitmap
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/**
 * Orquesta la animación de paso de página (RDR-009) con la técnica de la "captura":
 * se toma una foto de la página actual, se pone encima, se pasa de página en el navegador sin animar
 * (queda debajo) y se animan las dos capas según [PageTurnTransform].
 *
 * Readium no ofrece animaciones de página personalizables, por eso esto vive en nuestra capa de UI.
 */
@Stable
class PageTurnController {
    private val progress = Animatable(1f)

    /** Captura de la página que sale. Es null cuando no hay animación en curso. */
    var snapshot by mutableStateOf<ImageBitmap?>(null)
        private set

    var direction by mutableStateOf(PageTurnDirection.Forward)
        private set

    /** Mientras es true no se aceptan otros pasos de página. */
    @Volatile
    var busy: Boolean = false
        private set

    /** Transformación actual de las dos capas. En reposo deja las dos en su sitio. */
    fun transform(pageWidth: Float): PageTurnTransform =
        PageTurnTransform.at(progress.value, direction, pageWidth)

    /**
     * @param capture toma la foto de la página actual; null si no se pudo, y entonces se pasa sin animar.
     * @param navigate pasa de página en el navegador y dice si se movió (false en la última página).
     * @param awaitNavigated espera a que el navegador muestre la página nueva.
     */
    suspend fun run(
        direction: PageTurnDirection,
        capture: suspend () -> Bitmap?,
        navigate: () -> Boolean,
        awaitNavigated: suspend () -> Unit,
    ) {
        if (busy) return
        busy = true
        var bitmap: Bitmap? = null
        try {
            bitmap = capture()
            if (bitmap == null) {
                navigate()
                return
            }
            this.direction = direction
            progress.snapTo(0f)
            // La captura se pone antes de navegar para que el cambio de página quede tapado.
            snapshot = bitmap.asImageBitmap()
            if (!navigate()) return
            awaitNavigated()
            progress.animateTo(1f, tween(DURATION_MS, easing = FastOutSlowInEasing))
        } finally {
            withContext(NonCancellable) {
                snapshot = null
                progress.snapTo(1f)
                bitmap?.recycle()
                busy = false
            }
        }
    }

    companion object {
        const val DURATION_MS = 300
    }
}
