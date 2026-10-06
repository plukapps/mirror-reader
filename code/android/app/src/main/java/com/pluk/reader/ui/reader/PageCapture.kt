package com.pluk.reader.ui.reader

import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.Window
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Captura los píxeles que hay ahora en [area] (coordenadas de la ventana), incluido el fondo de la página.
 * Devuelve null si no se pudo, y entonces el paso de página se hace sin animación.
 *
 * Se usa PixelCopy y no `View.draw` porque el contenido del WebView solo sale bien desde el compositor.
 */
suspend fun captureWindowArea(window: Window, area: Rect): Bitmap? {
    if (area.isEmpty) return null
    val bitmap = Bitmap.createBitmap(area.width(), area.height(), Bitmap.Config.ARGB_8888)
    return suspendCancellableCoroutine { continuation ->
        PixelCopy.request(
            window,
            area,
            bitmap,
            { result ->
                if (result == PixelCopy.SUCCESS) {
                    continuation.resume(bitmap)
                } else {
                    bitmap.recycle()
                    continuation.resume(null)
                }
            },
            Handler(Looper.getMainLooper()),
        )
    }
}
