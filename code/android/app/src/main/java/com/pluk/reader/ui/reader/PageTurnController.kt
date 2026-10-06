package com.pluk.reader.ui.reader

import android.graphics.Bitmap
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

/** Lo que el controlador necesita del navegador de EPUB, para no depender de Readium. */
interface PageTurnOps {
    /** Foto de la página que se ve ahora, o null si no se pudo (entonces se pasa sin animar). */
    suspend fun capture(): Bitmap?

    /** Como [capture], pero solo si se puede hacer ya, sin preparar la pantalla antes. Null si no. */
    suspend fun captureIfReady(): Bitmap?

    /** Pasa de página en el navegador sin animar, en la dirección visual dada. False si no se movió. */
    fun move(direction: PageTurnDirection): Boolean

    /** Espera a que el navegador muestre la página a la que se acaba de mover. */
    suspend fun awaitMoved()
}

/**
 * Orquesta la animación de paso de página (RDR-009) con la técnica de la "captura":
 * se toma una foto de la página actual, se pone encima, se pasa de página en el navegador sin animar
 * (queda debajo) y se animan las dos capas según [PageTurnTransform].
 *
 * Una sesión empieza con [begin] y la termina [release]. Mientras el dedo sigue abajo, [update] mueve la
 * animación con el dedo. Un toque en el borde es una sesión que se suelta enseguida, confirmada.
 *
 * Readium no ofrece animaciones de página personalizables, por eso esto vive en nuestra capa de UI.
 */
@Stable
class PageTurnController(private val scope: CoroutineScope) {
    private val progress = Animatable(1f)

    /** Captura de la página que sale. Es null cuando no hay animación en curso. */
    var snapshot by mutableStateOf<ImageBitmap?>(null)
        private set

    var direction by mutableStateOf(PageTurnDirection.Forward)
        private set

    /** Mientras es true no se aceptan otras sesiones. */
    @Volatile
    var busy: Boolean = false
        private set

    private var prefetched: Deferred<Bitmap?>? = null

    /** Identifica la sesión en curso. Una sesión abortada ya no toca el estado de la siguiente. */
    private var sessionId = 0
    private var sessionJob: Job? = null
    private var sessionBitmap: Bitmap? = null

    /** True cuando la página ya cambió y solo falta que termine de deslizarse la captura. */
    @Volatile
    private var settlingCommitted = false

    @Volatile
    private var releaseVelocity = 0f

    @Volatile
    private var dragProgress = 0f

    /** null mientras el dedo sigue abajo; true si se completa y false si se cancela. */
    @Volatile
    private var decision: Boolean? = null

    /** Transformación actual de las dos capas. En reposo deja las dos en su sitio. */
    fun transform(pageWidth: Float): PageTurnTransform =
        PageTurnTransform.at(progress.value, direction, pageWidth)

    /**
     * Captura la página en cuanto el dedo toca la pantalla, para que al empezar el arrastre la foto ya esté
     * lista. Cada toque reemplaza a la captura anterior.
     */
    fun prefetch(ops: PageTurnOps) {
        // Si la animación anterior ya pasó de página, tocar la pantalla la termina al instante: así se puede
        // pasar de página seguido sin esperar. La captura espera a que se vea la página de verdad.
        val aborted = abortSettlingSession()
        android.util.Log.d("PTLOG", "down: aborted=$aborted busy=$busy")
        if (busy) return
        discardPrefetch()
        prefetched = scope.async {
            if (aborted) repeat(2) { withFrameNanos { } }
            ops.captureIfReady()
        }
    }

    /** Termina de golpe una sesión que ya cambió de página y solo se está asentando. True si había una. */
    private fun abortSettlingSession(): Boolean {
        if (!busy || !settlingCommitted) return false
        sessionId++
        sessionJob?.cancel()
        sessionJob = null
        settlingCommitted = false
        snapshot = null
        releaseLater(sessionBitmap)
        sessionBitmap = null
        decision = null
        busy = false
        return true
    }

    /** Libera una captura que ya no se dibuja, un par de frames después para no recortar el último dibujado. */
    private fun releaseLater(bitmap: Bitmap?) {
        if (bitmap == null) return
        scope.launch {
            repeat(2) { withFrameNanos { } }
            bitmap.recycle()
        }
    }

    private fun discardPrefetch() {
        val old = prefetched ?: return
        prefetched = null
        scope.launch { runCatching { old.await() }.getOrNull()?.recycle() }
    }

    /** Empieza una sesión. Devuelve false si ya hay una en curso, y entonces no hay que llamar a [update] ni [release]. */
    fun begin(direction: PageTurnDirection, initialProgress: Float, ops: PageTurnOps): Boolean {
        if (busy) { android.util.Log.d("PTLOG", "begin REFUSED settling=$settlingCommitted"); return false }
        busy = true
        dragProgress = initialProgress.coerceIn(0f, 1f)
        decision = null
        val id = ++sessionId
        android.util.Log.d("PTLOG", "begin ok id=$id")
        sessionJob = scope.launch { runSession(id, direction, ops) }
        return true
    }

    /** Mueve la animación con el dedo (avance 0..1). */
    fun update(progress: Float) {
        dragProgress = progress.coerceIn(0f, 1f)
    }

    /**
     * El dedo se soltó: completa el paso de página o lo cancela.
     *
     * @param velocity velocidad del dedo en avance de animación por segundo, positiva hacia completar el giro.
     */
    fun release(commit: Boolean, velocity: Float = 0f) {
        releaseVelocity = velocity
        decision = commit
    }

    private suspend fun awaitDecision(): Boolean {
        while (decision == null) withFrameNanos { }
        return decision == true
    }

    private suspend fun runSession(id: Int, direction: PageTurnDirection, ops: PageTurnOps) {
        var bitmap: Bitmap? = null
        val early = prefetched
        prefetched = null
        try {
            bitmap = early?.let { runCatching { it.await() }.getOrNull() } ?: ops.capture()
            android.util.Log.d("PTLOG", "bitmap=${bitmap != null} id=$id")
            if (bitmap == null) {
                if (awaitDecision()) ops.move(direction)
                return
            }
            sessionBitmap = bitmap
            this.direction = direction
            progress.snapTo(dragProgress)
            // La captura se pone antes de navegar para que el cambio de página quede tapado.
            snapshot = bitmap.asImageBitmap()
            // Mientras el dedo sigue abajo la animación lo acompaña, también mientras el navegador cambia
            // de página: si no, la captura se queda quieta al inicio del gesto y luego salta al dedo.
            val tracker = scope.launch {
                while (decision == null) {
                    progress.snapTo(dragProgress)
                    withFrameNanos { }
                }
            }
            try {
                val moved = ops.move(direction)
                android.util.Log.d("PTLOG", "move($direction)=$moved id=$id")
                if (!moved) return
                ops.awaitMoved()
                android.util.Log.d("PTLOG", "awaitMoved done id=$id")
                tracker.join()
            } finally {
                tracker.cancel()
            }
            val commit = decision == true
            val dragged = dragProgress > PageTurnGesture.MIN_PROGRESS_TO_FOLLOW
            settlingCommitted = commit
            val target = if (commit) 1f else 0f
            if (dragged) {
                // Se parte de la velocidad del dedo y se frena sin cortes: un resorte sin rebote.
                progress.animateTo(
                    target,
                    PageTurnGesture.settleSpring(),
                    PageTurnGesture.settleVelocity(releaseVelocity, progress.value, commit),
                )
            } else {
                // Un toque en el borde parte del reposo: acelera y frena.
                val remaining = abs(target - progress.value)
                progress.animateTo(target, tween(PageTurnGesture.settleDurationMs(remaining), easing = FastOutSlowInEasing))
            }
            if (!commit) {
                // Cancelado: la página vieja ya está cubriendo todo, se vuelve a ella debajo de la captura.
                ops.move(direction.opposite())
                ops.awaitMoved()
            }
        } finally {
            withContext(NonCancellable) {
                // Si la sesión fue abortada, [abortSettlingSession] ya limpió y quizá ya empezó otra.
                if (id == sessionId) {
                    snapshot = null
                    progress.snapTo(1f)
                    settlingCommitted = false
                    sessionBitmap = null
                    releaseLater(bitmap)
                    decision = null
                    busy = false
                }
            }
        }
    }

    companion object {
        const val DURATION_MS = 300
    }
}

private fun PageTurnDirection.opposite() =
    if (this == PageTurnDirection.Forward) PageTurnDirection.Backward else PageTurnDirection.Forward
