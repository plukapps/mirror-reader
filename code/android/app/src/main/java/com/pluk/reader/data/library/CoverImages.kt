package com.pluk.reader.data.library

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import java.io.File
import java.io.IOException

/** Alto máximo de una portada guardada: alcanza para la grilla y el detalle, y pesa unas decenas de KB (LIB-012). */
const val MAX_COVER_HEIGHT = 600

/** Guarda [bitmap] como portada JPEG en [target], achicada a [MAX_COVER_HEIGHT] si hace falta. */
fun saveCover(bitmap: Bitmap, target: File): Boolean = try {
    val scale = MAX_COVER_HEIGHT.toFloat() / bitmap.height
    val scaled = if (scale < 1f) {
        Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt().coerceAtLeast(1), MAX_COVER_HEIGHT, true)
    } else {
        bitmap
    }
    target.outputStream().use { scaled.compress(Bitmap.CompressFormat.JPEG, 85, it) }
} catch (_: IOException) {
    target.delete()
    false
}

/**
 * Lee la imagen que eligió el usuario (LIB-019) ya achicada cerca de [MAX_COVER_HEIGHT], o null si no se pudo.
 * Desde Android 9 respeta la orientación de la foto.
 */
fun decodeCoverImage(resolver: ContentResolver, uri: Uri): Bitmap? = try {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(resolver, uri)) { decoder, info, _ ->
            val height = info.size.height
            if (height > MAX_COVER_HEIGHT) {
                val width = (info.size.width.toLong() * MAX_COVER_HEIGHT / height).toInt().coerceAtLeast(1)
                decoder.setTargetSize(width, MAX_COVER_HEIGHT)
            }
            // Un bitmap de hardware no se puede comprimir a JPEG.
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
    } else {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (bounds.outHeight / (sample * 2) >= MAX_COVER_HEIGHT) sample *= 2
        resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        }
    }
} catch (_: IOException) {
    null
} catch (_: SecurityException) {
    null
}
