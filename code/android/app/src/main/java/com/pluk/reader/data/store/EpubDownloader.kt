package com.pluk.reader.data.store

import android.content.Context
import android.net.Uri
import com.pluk.reader.domain.model.ImportOutcome
import com.pluk.reader.domain.repository.LibraryRepository
import com.pluk.reader.domain.store.DeliveryOutcome
import com.pluk.reader.domain.store.StoreDelivery
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Entrega un libro de la tienda (STO-003): descarga el EPUB a un temporal y lo importa por el mismo
 * camino que un archivo elegido por el usuario (LIB-001 a LIB-003).
 */
class EpubDownloader @Inject constructor(
    @ApplicationContext context: Context,
    private val library: LibraryRepository,
) : StoreDelivery {
    private val cacheDir = context.applicationContext.cacheDir

    override suspend fun deliver(sources: List<String>): DeliveryOutcome = deliverFirst(
        sources = sources,
        download = { url -> withContext(Dispatchers.IO) { download(url) } },
        import = { file -> library.import(Uri.fromFile(file).toString()) },
    )

    private fun download(url: String): File {
        val connection = URL(url).openConnection() as HttpURLConnection
        val file = File.createTempFile("store-", ".epub", cacheDir)
        try {
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("User-Agent", USER_AGENT)
            if (connection.responseCode != HttpURLConnection.HTTP_OK) throw IOException("HTTP ${connection.responseCode}")
            if (connection.contentLengthLong > MAX_BYTES) throw IOException("demasiado grande")
            connection.inputStream.use { input ->
                file.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var total = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        total += read
                        if (total > MAX_BYTES) throw IOException("demasiado grande")
                        output.write(buffer, 0, read)
                    }
                }
            }
            return file
        } catch (e: IOException) {
            file.delete()
            throw e
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val TIMEOUT_MS = 20_000
        const val MAX_BYTES = 100L * 1024 * 1024
    }
}

/**
 * Prueba [sources] en orden hasta que una se descarga y se importa (STO-003). Si el libro ya estaba
 * en la biblioteca también cuenta como entregado (STO-008). El temporal se borra siempre.
 */
suspend fun deliverFirst(
    sources: List<String>,
    download: suspend (url: String) -> File,
    import: suspend (File) -> ImportOutcome,
): DeliveryOutcome {
    for (url in sources) {
        val file = try {
            download(url)
        } catch (_: IOException) {
            continue
        }
        try {
            when (val outcome = import(file)) {
                is ImportOutcome.Imported -> return DeliveryOutcome.Delivered(outcome.bookId)
                is ImportOutcome.AlreadyInLibrary -> return DeliveryOutcome.Delivered(outcome.bookId)
                is ImportOutcome.Rejected -> Unit
            }
        } finally {
            file.delete()
        }
    }
    return DeliveryOutcome.Failed("No se pudo descargar el libro. Probá de nuevo.")
}
