package com.pluk.reader.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pluk.reader.R
import com.pluk.reader.domain.model.LibraryBook
import com.pluk.reader.domain.model.Note
import com.pluk.reader.domain.model.ReaderFont
import com.pluk.reader.ui.library.BookCover
import com.pluk.reader.ui.reader.ReaderIcons
import com.pluk.reader.ui.reader.labelFamily
import com.pluk.reader.ui.theme.MarginColors

/**
 * LIB-022: detalle del libro, diseño "04 — Book detail". Portada sobre amarillo, título, autor, sus notas (ANN-010)
 * y "Continuar leyendo". Si el libro deja de estar en la biblioteca, vuelve atrás.
 */
@Composable
fun BookDetailScreen(
    onBack: () -> Unit,
    onRead: (bookId: String) -> Unit,
    onOpenNote: (bookId: String, noteId: String) -> Unit,
    viewModel: BookDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    when (val current = state) {
        BookDetailUiState.Loading -> Box(Modifier.fillMaxSize().background(MarginColors.Paper))
        BookDetailUiState.Gone -> LaunchedEffect(Unit) { onBack() }
        is BookDetailUiState.Ready -> BookDetailContent(
            book = current.book,
            notes = current.notes,
            onBack = onBack,
            onRead = { onRead(current.book.id) },
            onOpenNote = { onOpenNote(current.book.id, it.id) },
        )
    }
}

/** Sin ViewModel, para poder probarla y previsualizarla. */
@Composable
fun BookDetailContent(
    book: LibraryBook,
    notes: List<Note>,
    onBack: () -> Unit,
    onRead: () -> Unit,
    onOpenNote: (Note) -> Unit,
) {
    val serif = ReaderFont.SERIF.labelFamily()
    Box(Modifier.fillMaxSize().background(MarginColors.Paper)) {
        LazyColumn(Modifier.fillMaxSize().testTag("book-detail"), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 104.dp)) {
            item {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .background(MarginColors.Yellow)
                        .windowInsetsPadding(WindowInsets.statusBars),
                ) {
                    Box(
                        Modifier
                            .padding(8.dp)
                            .size(48.dp)
                            .clip(CircleShape)
                            .clickable(role = Role.Button, onClick = onBack)
                            .testTag("detail-back"),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(ReaderIcons.ArrowBack, contentDescription = stringResource(R.string.detail_back), tint = MarginColors.OnYellow)
                    }
                    BookCover(book, Modifier.align(Alignment.Center).padding(top = 48.dp, bottom = 28.dp).width(132.dp))
                }
            }
            item {
                Column(Modifier.padding(horizontal = 24.dp, vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        book.title,
                        style = TextStyle(fontFamily = serif, fontSize = 30.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.02).em),
                        color = MarginColors.Ink,
                        modifier = Modifier.semantics { heading() }.testTag("detail-title"),
                    )
                    book.author?.let { Text(it, color = MarginColors.Muted, fontSize = 14.sp) }
                }
                HorizontalDivider(Modifier.padding(horizontal = 24.dp), color = MarginColors.Ink, thickness = 1.5.dp)
                Text(
                    pluralStringResource(R.plurals.detail_notes_count, notes.size, notes.size),
                    color = MarginColors.Ink,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 4.dp).testTag("detail-notes-count"),
                )
                if (notes.isEmpty()) {
                    Text(
                        stringResource(R.string.detail_notes_empty),
                        color = MarginColors.Muted,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                    )
                }
            }
            items(notes, key = { it.id }) { note ->
                NoteRow(note, serif, onClick = { onOpenNote(note) })
            }
        }
        ContinueReadingButton(
            percent = book.progressPercent,
            onClick = onRead,
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(horizontal = 20.dp, vertical = 16.dp),
        )
    }
}

/** ANN-010: el porcentaje del libro, el texto citado y la nota. Tocarla abre el libro en ese lugar. */
@Composable
private fun NoteRow(note: Note, serif: androidx.compose.ui.text.font.FontFamily, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 14.dp)
            .testTag("detail-note-${note.id}"),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(stringResource(R.string.detail_note_percent, note.percent), color = MarginColors.Muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        Row(Modifier.height(IntrinsicSize.Min)) {
            Box(Modifier.width(3.dp).fillMaxHeight().background(MarginColors.Yellow))
            Spacer(Modifier.width(10.dp))
            Text(
                note.quote,
                style = TextStyle(fontFamily = serif, fontStyle = FontStyle.Italic, fontSize = 16.sp),
                color = MarginColors.Ink,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(note.text, color = MarginColors.Ink, fontSize = 14.sp)
    }
    HorizontalDivider(Modifier.padding(horizontal = 24.dp), color = MarginColors.Line, thickness = 1.dp)
}

/** "Continuar leyendo" con el progreso (LIB-017, LIB-022), como el botón oscuro del diseño. */
@Composable
private fun ContinueReadingButton(percent: Int?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(MarginColors.InkFill)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 24.dp)
            .testTag("detail-continue"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(if (percent == null) R.string.detail_start_reading else R.string.detail_continue_reading),
            color = MarginColors.Yellow,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
        )
        percent?.let { Text("$it %", color = MarginColors.OnInkFill, fontSize = 15.sp, fontWeight = FontWeight.SemiBold) }
    }
}

@Preview(showBackground = true, widthDp = 380, heightDp = 820)
@Composable
private fun BookDetailPreview() {
    BookDetailContent(
        book = LibraryBook("b", "Meditaciones", "Marco Aurelio", null, 42),
        notes = listOf(
            Note("1", "b", "{}", "Pero yo, que he visto la naturaleza del bien, que es bello…", "Para la revisión del lunes.", 0.42, 1, 1),
            Note("2", "b", "{}", "El universo es cambio; la vida es lo que nuestros pensamientos hacen de ella.", "Clave.", 0.81, 2, 2),
        ),
        onBack = {},
        onRead = {},
        onOpenNote = {},
    )
}
