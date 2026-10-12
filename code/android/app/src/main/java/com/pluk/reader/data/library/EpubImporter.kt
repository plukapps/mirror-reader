package com.pluk.reader.data.library

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.pluk.reader.data.epub.PublicationLoader
import com.pluk.reader.data.local.db.BookDao
import com.pluk.reader.data.local.db.BookEntity
import com.pluk.reader.domain.model.ImportOutcome
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.readium.r2.shared.publication.services.cover

/**
 * Importa un EPUB: lo copia al almacenamiento privado calculando su hash (LIB-003), lo valida
 * con Readium (LIB-002), lee título, autor y portada (LIB-004) y lo registra en la base (ADR 0006).
 */
@Singleton
class EpubImporter @Inject constructor(
    @ApplicationContext context: Context,
    private val files: LibraryFiles,
    private val loader: PublicationLoader,
    private val dao: BookDao,
) {
    private val contentResolver = context.applicationContext.contentResolver

    // Dos importaciones del mismo archivo a la vez no deben duplicarlo.
    private val mutex = Mutex()

    suspend fun import(uri: Uri): ImportOutcome = withContext(Dispatchers.IO) {
        mutex.withLock { importLocked(uri) }
    }

    private suspend fun importLocked(uri: Uri): ImportOutcome {
        val temp = files.newTempFile()
        val hash = try {
            val input = contentResolver.openInputStream(uri) ?: throw IOException("sin flujo")
            input.use { source -> temp.outputStream().use { copyWithSha256(source, it) } }
        } catch (_: IOException) {
            temp.delete()
            return ImportOutcome.Rejected("No se pudo leer el archivo.")
        } catch (_: SecurityException) {
            temp.delete()
            return ImportOutcome.Rejected("No se pudo leer el archivo.")
        }

        // Un libro solo en la nube (LIB-007) no cuenta como ya importado: falta su archivo.
        val existing = dao.get(hash)
        if (existing?.isDownloaded == true) {
            temp.delete()
            return ImportOutcome.AlreadyInLibrary(hash, existing.title)
        }

        val bookFile = files.bookFile(hash)
        if (!temp.renameTo(bookFile)) {
            temp.delete()
            return ImportOutcome.Rejected("No se pudo guardar el libro.")
        }
        val publication = loader.load(Uri.fromFile(bookFile)).getOrElse {
            bookFile.delete()
            return ImportOutcome.Rejected(it.message ?: "No se pudo abrir el libro.")
        }
        try {
            val title = publication.metadata.title?.takeIf { it.isNotBlank() } ?: displayName(uri)
            val author = publication.metadata.authors
                .mapNotNull { it.name.takeIf(String::isNotBlank) }
                .joinToString(", ")
                .ifBlank { null }
            val hasCover = publication.cover()?.let { saveCover(it, files.coverFile(hash)) } ?: false
            if (existing != null) {
                // Conserva los metadatos de la nube y suma el archivo y la portada.
                dao.markDownloaded(hash, hasCover)
                return ImportOutcome.Imported(hash, existing.title)
            }
            // Si el usuario lo había quitado (LIB-020), importarlo de nuevo lo devuelve a la biblioteca.
            dao.deleteRemoved(hash)
            dao.insert(BookEntity(hash, title, author, hasCover, System.currentTimeMillis(), sizeBytes = bookFile.length()))
            return ImportOutcome.Imported(hash, title)
        } finally {
            publication.close()
        }
    }

    private fun displayName(uri: Uri): String {
        val name = contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { if (it.moveToFirst()) it.getString(0) else null }
            ?: uri.lastPathSegment
            ?: "Sin título"
        return name.removeSuffix(".epub").ifBlank { "Sin título" }
    }
}
