package com.pluk.reader.ui.actions

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pluk.reader.R
import com.pluk.reader.domain.model.LibraryBook
import com.pluk.reader.ui.theme.MarginColors

/**
 * Menú de un libro con todo lo que dispara: lector, confirmación de quitar (LIB-020), selector de fotos (LIB-019) y
 * avisos. Va en la raíz, encima de las pantallas y de la barra inferior.
 */
@Composable
fun BookActionsHost(
    state: BookActionsState,
    onRead: (bookId: String) -> Unit,
    onDetails: (bookId: String) -> Unit,
    bottomPadding: Dp,
    viewModel: BookActionsViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(viewModel) {
        viewModel.messages.collect { snackbar.showSnackbar(it.toText(context)) }
    }
    // El selector de fotos es otra actividad: el libro elegido se guarda por si la nuestra se recrea.
    var coverFor by rememberSaveable { mutableStateOf<String?>(null) }
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        val bookId = coverFor
        coverFor = null
        if (uri != null && bookId != null) viewModel.changeCover(bookId, uri.toString())
    }
    var confirmRemove by remember { mutableStateOf<LibraryBook?>(null) }

    Box(Modifier.fillMaxSize()) {
        state.target?.let { target ->
            BookActionsMenu(
                target = target,
                onDismiss = state::close,
                onAction = { action ->
                    state.close()
                    val book = target.book
                    when (action) {
                        BookAction.Read -> onRead(book.id)
                        BookAction.Details -> onDetails(book.id)
                        BookAction.MarkFinished -> viewModel.markFinished(book)
                        BookAction.MarkUnread -> viewModel.markUnread(book)
                        BookAction.ChangeCover -> {
                            coverFor = book.id
                            photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        }
                        BookAction.Remove -> confirmRemove = book
                    }
                },
            )
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = bottomPadding))
    }

    confirmRemove?.let { book ->
        AlertDialog(
            onDismissRequest = { confirmRemove = null },
            title = { Text(stringResource(R.string.book_actions_remove_title, book.title)) },
            text = { Text(stringResource(R.string.book_actions_remove_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmRemove = null
                        viewModel.remove(book)
                    },
                    modifier = Modifier.testTag("confirm-remove"),
                ) {
                    Text(stringResource(R.string.book_actions_remove_confirm), color = MarginColors.Danger, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmRemove = null }) {
                    Text(stringResource(R.string.book_actions_cancel), color = MarginColors.Ink)
                }
            },
            // Colores explícitos: el tema de Material no es el de la app y el texto salía casi blanco.
            containerColor = MarginColors.Field,
            titleContentColor = MarginColors.Ink,
            textContentColor = MarginColors.Ink,
        )
    }
}

private fun BookActionMessage.toText(context: android.content.Context): String = when (this) {
    BookActionMessage.MarkedFinished -> context.getString(R.string.book_actions_marked_finished)
    BookActionMessage.MarkedUnread -> context.getString(R.string.book_actions_marked_unread)
    BookActionMessage.CoverFailed -> context.getString(R.string.book_actions_cover_failed)
    is BookActionMessage.Removed -> context.getString(R.string.book_actions_removed, title)
}
