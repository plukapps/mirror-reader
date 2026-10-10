package com.pluk.reader.ui.search

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.pluk.reader.data.store.USER_AGENT
import com.pluk.reader.domain.model.LibraryBook
import com.pluk.reader.domain.store.StoreBook
import com.pluk.reader.ui.library.BookCover
import com.pluk.reader.ui.theme.MarginColors
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Portada de un libro del catálogo (STO-002). Mientras carga, o si no hay, la portada generada de
 * la biblioteca. Prueba de concepto: un cargador mínimo con caché en memoria, sin librería de imágenes.
 */
@Composable
fun StoreCover(book: StoreBook, modifier: Modifier = Modifier) {
    val bitmap by produceState(RemoteCovers.cached(book.coverUrl), book.coverUrl) {
        if (value == null) value = book.coverUrl?.let { RemoteCovers.load(it) }
    }
    val cover = bitmap
    if (cover != null) {
        Image(
            cover.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier.aspectRatio(2f / 3f).clip(CoverShape).border(3.dp, MarginColors.CoverBorder, CoverShape),
        )
    } else {
        BookCover(LibraryBook(book.key, book.title, book.author, coverPath = null, progressPercent = null), modifier)
    }
}

private val CoverShape = RoundedCornerShape(4.dp)

private object RemoteCovers {
    private val cache = LruCache<String, Bitmap>(60)

    fun cached(url: String?): Bitmap? = url?.let(cache::get)

    suspend fun load(url: String): Bitmap? = withContext(Dispatchers.IO) {
        cache.get(url)?.let { return@withContext it }
        val connection = try {
            URL(url).openConnection() as HttpURLConnection
        } catch (_: IOException) {
            return@withContext null
        }
        try {
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000
            connection.setRequestProperty("User-Agent", USER_AGENT)
            if (connection.responseCode != HttpURLConnection.HTTP_OK) return@withContext null
            connection.inputStream.use { BitmapFactory.decodeStream(it) }?.also { cache.put(url, it) }
        } catch (_: IOException) {
            null
        } finally {
            connection.disconnect()
        }
    }
}
