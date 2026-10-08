package com.pluk.reader.ui.library

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pluk.reader.R
import com.pluk.reader.domain.model.LibraryBook
import com.pluk.reader.domain.model.LibraryFilter
import com.pluk.reader.domain.model.ReadingStatus
import com.pluk.reader.ui.theme.MarginColors

/** Pantalla de inicio: la biblioteca del usuario (LIB-001, LIB-010, LIB-011). */
@Composable
fun LibraryScreen(onBookClick: (bookId: String) -> Unit, viewModel: LibraryViewModel = hiltViewModel()) {
    val state = viewModel.uiState.collectAsStateWithLifecycle().value
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    LaunchedEffect(viewModel) {
        viewModel.messages.collect { snackbar.showSnackbar(it.toText(context)) }
    }
    LibraryContent(
        state = state,
        snackbar = snackbar,
        onFilterSelected = viewModel::onFilterSelected,
        onImport = viewModel::onImport,
        onBookClick = onBookClick,
        onUpload = viewModel::onUpload,
    )
}

/** Sin ViewModel, para poder probarla y previsualizarla. */
@Composable
fun LibraryContent(
    state: LibraryUiState,
    snackbar: SnackbarHostState,
    onFilterSelected: (LibraryFilter) -> Unit,
    onImport: (List<String>) -> Unit,
    onBookClick: (String) -> Unit,
    onUpload: () -> Unit = {},
) {
    // El libro se copia a la app (ADR 0006), así que no hace falta permiso persistente.
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        onImport(uris.map { it.toString() })
    }
    val launchPicker = { picker.launch(arrayOf(EPUB_MIME_TYPE)) }

    Box(Modifier.fillMaxSize().background(MarginColors.Paper)) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Header(importing = state.importing, onImport = launchPicker)
            if (state.signedIn && (state.pendingUploadCount > 0 || state.uploading)) {
                UploadBar(state.pendingUploadCount, state.uploading, onUpload)
            }
            Tabs(state, onFilterSelected)
            when {
                state.loading -> Unit
                state.isLibraryEmpty -> EmptyLibrary(onImport = launchPicker)
                else -> BookGrid(state.books, onBookClick)
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding())
    }
}

@Composable
private fun Header(importing: Boolean, onImport: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.library_title),
            color = MarginColors.Ink,
            fontSize = 44.sp,
            lineHeight = 44.sp,
            letterSpacing = (-0.045).em,
        )
        Row(
            Modifier
                .height(40.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(MarginColors.Yellow)
                .clickable(enabled = !importing, role = Role.Button, onClick = onImport)
                .padding(start = 10.dp, end = 14.dp)
                .testTag("import-button"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(LibraryIcons.Add, contentDescription = null, tint = MarginColors.Ink)
            Text(
                text = stringResource(if (importing) R.string.library_importing else R.string.library_import),
                color = MarginColors.Ink,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

/** LIB-007: libros importados que aún no están en la nube, con el botón para subirlos. */
@Composable
private fun UploadBar(pendingCount: Int, uploading: Boolean, onUpload: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 14.dp).testTag("upload-bar"),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = pluralStringResource(R.plurals.library_pending_upload, pendingCount, pendingCount),
            color = MarginColors.Muted,
            fontSize = 13.sp,
        )
        Box(
            Modifier
                .height(34.dp)
                .clip(RoundedCornerShape(17.dp))
                .background(MarginColors.Yellow)
                .clickable(enabled = !uploading, role = Role.Button, onClick = onUpload)
                .padding(horizontal = 14.dp)
                .testTag("upload-button"),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(if (uploading) R.string.library_uploading else R.string.library_upload),
                color = MarginColors.Ink,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun Tabs(state: LibraryUiState, onSelected: (LibraryFilter) -> Unit) {
    Column(Modifier.padding(top = 18.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Tab(R.string.library_tab_all, state.allCount, LibraryFilter.All, state.filter, onSelected, modifier = Modifier.weight(1f))
            Tab(R.string.library_tab_reading, state.readingCount, LibraryFilter.Reading, state.filter, onSelected, modifier = Modifier.weight(1f))
            Tab(R.string.library_tab_finished, state.finishedCount, LibraryFilter.Finished, state.filter, onSelected, modifier = Modifier.weight(1f))
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(MarginColors.Line))
    }
}

@Composable
private fun Tab(
    label: Int,
    count: Int,
    filter: LibraryFilter,
    current: LibraryFilter,
    onSelected: (LibraryFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    val selected = filter == current
    Column(
        modifier = modifier
            .clickable(role = Role.Tab) { onSelected(filter) }
            .semantics { this.selected = selected }
            .testTag("tab-${filter.name}"),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "${stringResource(label)} $count",
            color = if (selected) MarginColors.Ink else MarginColors.Muted,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            modifier = Modifier.padding(bottom = 10.dp),
        )
        // La barra de la pestaña activa se superpone a la línea inferior, como en el diseño.
        Box(
            Modifier.fillMaxWidth().height(3.dp).background(if (selected) MarginColors.Ink else androidx.compose.ui.graphics.Color.Transparent),
        )
    }
}

@Composable
private fun BookGrid(books: List<LibraryBook>, onBookClick: (String) -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 96.dp),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize().testTag("book-grid"),
    ) {
        items(books, key = { it.id }) { BookCell(it, onClick = { onBookClick(it.id) }) }
    }
}

@Composable
private fun BookCell(book: LibraryBook, onClick: () -> Unit) {
    val statusText = when (book.status) {
        ReadingStatus.New -> stringResource(R.string.library_status_new)
        ReadingStatus.Finished -> stringResource(R.string.library_status_finished)
        ReadingStatus.Reading -> "${book.progressPercent} %"
    }
    val cloudText = stringResource(R.string.library_status_cloud)
    val description = listOfNotNull(book.title, book.author, statusText, cloudText.takeIf { !book.isDownloaded }).joinToString(". ")
    Column(
        Modifier
            .clickable(role = Role.Button, onClick = onClick)
            .clearAndSetSemantics { contentDescription = description; role = Role.Button }
            .testTag("book-${book.id}"),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        BookCover(book)
        // LIB-007: un libro solo en la nube se distingue de los descargados.
        if (!book.isDownloaded) {
            Text(cloudText, color = MarginColors.Muted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.testTag("cloud-${book.id}"))
        }
        when (book.status) {
            ReadingStatus.Reading -> {
                LinearProgressIndicator(
                    progress = { (book.progressPercent ?: 0) / 100f },
                    modifier = Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)),
                    color = MarginColors.Ink,
                    trackColor = MarginColors.Line,
                    strokeCap = StrokeCap.Round,
                    gapSize = 0.dp,
                    drawStopIndicator = {},
                )
                Text(statusText, color = MarginColors.Muted, fontSize = 11.sp, fontWeight = FontWeight.Medium)
            }
            ReadingStatus.Finished -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(LibraryIcons.Check, contentDescription = null, tint = MarginColors.Ink, modifier = Modifier.height(14.dp))
                Text(statusText, color = MarginColors.Ink, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            }
            ReadingStatus.New ->
                Text(statusText, color = MarginColors.Muted, fontSize = 11.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun EmptyLibrary(onImport: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp).testTag("empty-library"),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.library_empty_title), color = MarginColors.Ink, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
        Text(
            stringResource(R.string.library_empty_body),
            color = MarginColors.Muted,
            fontSize = 14.sp,
            modifier = Modifier.padding(top = 8.dp, bottom = 20.dp),
        )
        Row(
            Modifier
                .height(44.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(MarginColors.Ink)
                .clickable(role = Role.Button, onClick = onImport)
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.library_import_first), color = MarginColors.Yellow, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
    }
}

private const val EPUB_MIME_TYPE = "application/epub+zip"

@Preview(widthDp = 360, heightDp = 780)
@Composable
private fun LibraryPreview() {
    val books = listOf(
        LibraryBook("a", "MEDI-\nTATIONS", "Marcus Aurelius", null, 42),
        LibraryBook("b", "MOBY\n—DICK", null, null, 8),
        LibraryBook("c", "The Odyssey", null, null, 71),
        LibraryBook("d", "Pride & Prejudice", null, null, 100),
        LibraryBook("e", "DRAC-\nULA", null, null, null),
        LibraryBook("f", "Walden", null, null, 100),
    )
    LibraryContent(
        state = LibraryUiState(loading = false, books = books, allCount = 24, readingCount = 3, finishedCount = 12),
        snackbar = remember { SnackbarHostState() },
        onFilterSelected = {},
        onImport = {},
        onBookClick = {},
    )
}
