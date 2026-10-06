package com.pluk.reader.ui.reader

import android.graphics.Bitmap
import android.graphics.Rect
import android.view.Window
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.navigator.preferences.ReadingProgression
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Locator

/**
 * Conecta [PageTurnController] con el `EpubNavigatorFragment` de Readium.
 *
 * La dirección de la animación es visual (Forward = la página sale por la izquierda). Para pasar de página
 * en el libro se traduce según el sentido de lectura: en un libro de derecha a izquierda es al revés.
 */
@OptIn(ExperimentalReadiumApi::class)
class NavigatorPageTurnOps(
    private val navigator: EpubNavigatorFragment,
    private val window: Window,
    private val captureArea: () -> Rect?,
    /** Se ejecuta antes de capturar, por ejemplo para ocultar los controles que taparían la página. */
    private val beforeCapture: suspend () -> Unit,
    /** True si se puede capturar sin preparar nada antes (por ejemplo, sin controles encima). */
    private val isReady: () -> Boolean = { false },
) : PageTurnOps {
    private var before: Locator? = null

    override suspend fun capture(): Bitmap? {
        val area = captureArea() ?: run { android.util.Log.d("PTLOG", "capture: area null"); return null }
        android.util.Log.d("PTLOG", "capture: beforeCapture")
        beforeCapture()
        android.util.Log.d("PTLOG", "capture: pixelcopy")
        return captureWindowArea(window, area).also { android.util.Log.d("PTLOG", "capture: done ${it != null}") }
    }

    override suspend fun captureIfReady(): Bitmap? {
        if (!isReady()) return null
        val area = captureArea() ?: return null
        return captureWindowArea(window, area)
    }

    override fun move(direction: PageTurnDirection): Boolean {
        before = navigator.currentLocator.value
        return if (isForward(direction)) navigator.goForward(animated = false) else navigator.goBackward(animated = false)
    }

    override suspend fun awaitMoved() {
        val previous = before
        withTimeoutOrNull(NAVIGATION_TIMEOUT_MS) { navigator.currentLocator.first { it != previous } }
        repeat(2) { withFrameNanos { } }
    }

    /** Pasa de página sin animación, para cuando el efecto está desactivado. */
    fun moveWithoutAnimation(direction: PageTurnDirection) {
        move(direction)
    }

    private fun isForward(direction: PageTurnDirection): Boolean {
        val rtl = navigator.overflow.value.readingProgression == ReadingProgression.RTL
        return (direction == PageTurnDirection.Forward) != rtl
    }

    private companion object {
        const val NAVIGATION_TIMEOUT_MS = 400L
    }
}
