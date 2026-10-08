package com.pluk.reader.ui.library

import android.content.Context
import com.pluk.reader.R
import com.pluk.reader.domain.model.ImportOutcome
import com.pluk.reader.domain.usecase.UploadReport
import com.pluk.reader.domain.model.LibraryBook
import com.pluk.reader.domain.model.LibraryFilter

data class LibraryUiState(
    val loading: Boolean = true,
    /** Libros que pasan el filtro activo (LIB-010). */
    val books: List<LibraryBook> = emptyList(),
    val filter: LibraryFilter = LibraryFilter.All,
    val allCount: Int = 0,
    val readingCount: Int = 0,
    val finishedCount: Int = 0,
    val importing: Boolean = false,
    /** Hay una tanda de subida en curso. */
    val uploading: Boolean = false,
    /** Libros descargados que aún no están en la nube (LIB-007). */
    val pendingUploadCount: Int = 0,
    val signedIn: Boolean = false,
) {
    /** Se ofrece subir solo con sesión, con libros pendientes y sin otra subida en curso. */
    val canUpload: Boolean get() = signedIn && pendingUploadCount > 0 && !uploading

    /** No hay ningún libro, sin importar el filtro. */
    val isLibraryEmpty: Boolean get() = !loading && allCount == 0
}

/** Aviso al usuario tras importar (LIB-001, LIB-002, LIB-003). La pantalla lo traduce a texto. */
sealed interface LibraryMessage {
    data class Imported(val count: Int) : LibraryMessage
    data class AlreadyInLibrary(val title: String) : LibraryMessage
    data class Rejected(val reason: String) : LibraryMessage
    data class Uploaded(val count: Int) : LibraryMessage
    data class NotEnoughSpace(val count: Int) : LibraryMessage
    data class UploadFailed(val count: Int) : LibraryMessage
    data object CloudUnreachable : LibraryMessage
}

/** Avisos de una tanda de subida (LIB-009, SYN-008). Vacía si no había nada que subir. */
fun UploadReport.toMessages(): List<LibraryMessage> = listOfNotNull(
    uploaded.takeIf { it > 0 }?.let { LibraryMessage.Uploaded(it) },
    notEnoughSpace.takeIf { it > 0 }?.let { LibraryMessage.NotEnoughSpace(it) },
    failed.takeIf { it > 0 }?.let { LibraryMessage.UploadFailed(it) },
    LibraryMessage.CloudUnreachable.takeIf { unreachable },
)

/** Avisos de un lote de importación: primero el total importado, luego cada duplicado o rechazo. */
fun List<ImportOutcome>.toMessages(): List<LibraryMessage> {
    val imported = count { it is ImportOutcome.Imported }
    return listOfNotNull(imported.takeIf { it > 0 }?.let { LibraryMessage.Imported(it) }) + mapNotNull {
        when (it) {
            is ImportOutcome.AlreadyInLibrary -> LibraryMessage.AlreadyInLibrary(it.title)
            is ImportOutcome.Rejected -> LibraryMessage.Rejected(it.message)
            is ImportOutcome.Imported -> null
        }
    }
}

/** Texto del aviso para mostrar en un snackbar. */
fun LibraryMessage.toText(context: Context): String = when (this) {
    is LibraryMessage.Imported -> context.resources.getQuantityString(R.plurals.library_imported, count, count)
    is LibraryMessage.AlreadyInLibrary -> context.getString(R.string.library_already_in, title)
    is LibraryMessage.Rejected -> reason
    is LibraryMessage.Uploaded -> context.resources.getQuantityString(R.plurals.library_uploaded, count, count)
    is LibraryMessage.NotEnoughSpace -> context.resources.getQuantityString(R.plurals.library_no_space, count, count)
    is LibraryMessage.UploadFailed -> context.resources.getQuantityString(R.plurals.library_upload_failed, count, count)
    LibraryMessage.CloudUnreachable -> context.getString(R.string.library_cloud_unreachable)
}
