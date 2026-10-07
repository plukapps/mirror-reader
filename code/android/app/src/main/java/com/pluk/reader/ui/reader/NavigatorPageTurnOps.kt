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
    /** Bordes del libro ya descubiertos. Vive más que esta instancia, que se crea en cada gesto. */
    private val edges: BookEdges = BookEdges(),
) : PageTurnOps {
    private var before: Locator? = null
    private var movedForward = true

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

    override fun canMove(direction: PageTurnDirection): Boolean {
        val forward = isForward(direction)
        val current = navigator.currentLocator.value
        if (edges.isBlocked(forward, current)) return false
        // La primera página se reconoce por el progreso; el final solo se descubre al intentar avanzar.
        return forward || (current.locations.totalProgression ?: 1.0) > 0.0
    }

    override fun move(direction: PageTurnDirection): Boolean {
        before = navigator.currentLocator.value
        movedForward = isForward(direction)
        val moved = if (movedForward) navigator.goForward(animated = false) else navigator.goBackward(animated = false)
        if (!moved) before?.let { edges.block(movedForward, it) }
        return moved
    }

    override suspend fun awaitMoved(): Boolean {
        val previous = before
        val changed = withTimeoutOrNull(NAVIGATION_TIMEOUT_MS) { navigator.currentLocator.first { it != previous } } != null
        if (!changed && previous != null) edges.block(movedForward, previous)
        repeat(2) { withFrameNanos { } }
        return changed
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

/**
 * Recuerda desde qué página el navegador no pudo avanzar o retroceder, para no volver a animar un paso
 * que no cambia nada (RDR-009). Deja de valer en cuanto la página es otra.
 */
class BookEdges {
    private var forwardAt: Locator? = null
    private var backwardAt: Locator? = null

    fun isBlocked(forward: Boolean, at: Locator) = (if (forward) forwardAt else backwardAt) == at

    fun block(forward: Boolean, at: Locator) {
        if (forward) forwardAt = at else backwardAt = at
    }
}
