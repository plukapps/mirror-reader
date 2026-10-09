package com.pluk.reader.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pluk.reader.R
import com.pluk.reader.domain.model.LibraryBook
import com.pluk.reader.domain.model.ReadingStatus
import com.pluk.reader.domain.search.SearchScope
import com.pluk.reader.ui.library.BookCover
import com.pluk.reader.ui.navigation.LocalBottomBarPadding
import com.pluk.reader.ui.navigation.NavIcons
import com.pluk.reader.ui.theme.HostGrotesk
import com.pluk.reader.ui.theme.MarginColors

/** Búsqueda local en la biblioteca (LIB-006, LIB-013 a LIB-015). Diseño: "03 — Search". */
@Composable
fun SearchScreen(onBookClick: (bookId: String) -> Unit, viewModel: SearchViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    // El campo toma el foco solo la primera vez, no al volver del lector.
    var autoFocused by rememberSaveable { mutableStateOf(false) }
    SearchContent(
        query = query,
        state = state,
        requestFocus = !autoFocused,
        onFocusRequested = { autoFocused = true },
        onQueryChange = viewModel::onQueryChange,
        onClear = viewModel::onClear,
        onScopeChange = viewModel::onScopeChange,
        onBookClick = onBookClick,
    )
}

/** Sin ViewModel, para poder probarla y previsualizarla. */
@Composable
fun SearchContent(
    query: String,
    state: SearchUiState,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    onScopeChange: (SearchScope) -> Unit,
    onBookClick: (String) -> Unit,
    requestFocus: Boolean = false,
    onFocusRequested: () -> Unit = {},
) {
    val bottomBarPadding = LocalBottomBarPadding.current
    Box(Modifier.fillMaxSize().background(MarginColors.Paper), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier
                .widthIn(max = MAX_CONTENT_WIDTH)
                .fillMaxSize()
                .statusBarsPadding()
                .imePadding(),
        ) {
            Text(
                text = stringResource(R.string.search_title),
                color = MarginColors.Ink,
                fontSize = 44.sp,
                lineHeight = 44.sp,
                letterSpacing = (-0.045).em,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp),
            )
            SearchField(query, onQueryChange, onClear, requestFocus, onFocusRequested)
            ScopeChips(state.scope, onScopeChange)
            when {
                state.loading -> Unit
                !state.hasQuery -> Message(stringResource(R.string.search_help))
                state.results.isEmpty() -> Message(stringResource(R.string.search_no_results, query.trim()), Modifier.testTag("search-no-results"))
                else -> Results(state.results, onBookClick, PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp + bottomBarPadding))
            }
        }
    }
}

@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    requestFocus: Boolean,
    onFocusRequested: () -> Unit,
) {
    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(requestFocus) {
        if (requestFocus) {
            focusRequester.requestFocus()
            onFocusRequested()
        }
    }
    Row(
        Modifier
            .padding(start = 16.dp, end = 16.dp, top = 16.dp)
            .fillMaxWidth()
            .height(56.dp)
            .background(MarginColors.Field, FieldShape)
            .border(2.dp, MarginColors.Ink, FieldShape)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(NavIcons.Search, contentDescription = null, tint = MarginColors.Ink, modifier = Modifier.size(22.dp))
        val hint = stringResource(R.string.search_hint)
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            textStyle = FieldText,
            cursorBrush = SolidColor(MarginColors.Ink),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
            modifier = Modifier
                .weight(1f)
                .focusRequester(focusRequester)
                .semantics { contentDescription = hint }
                .testTag("search-field"),
            decorationBox = { field ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (query.isEmpty()) Text(hint, style = FieldText.copy(color = MarginColors.Muted), maxLines = 1)
                    field()
                }
            },
        )
        if (query.isNotEmpty()) {
            Icon(
                SearchIcons.Close,
                contentDescription = stringResource(R.string.search_clear),
                tint = MarginColors.Muted,
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(role = Role.Button, onClick = onClear)
                    .padding(2.dp)
                    .size(20.dp)
                    .testTag("search-clear"),
            )
        }
    }
}

/** LIB-015: "Todo" y "Autores". Los chips "En mi biblioteca" y "Gratis" del diseño son del catálogo. */
@Composable
private fun ScopeChips(current: SearchScope, onScopeChange: (SearchScope) -> Unit) {
    FlowRow(
        Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SearchScope.entries.forEach { scope ->
            val selected = scope == current
            Box(
                Modifier
                    .height(34.dp)
                    .clip(ChipShape)
                    .then(
                        if (selected) Modifier.background(MarginColors.Ink)
                        else Modifier.border(1.5.dp, MarginColors.Ink, ChipShape),
                    )
                    .clickable(role = Role.Tab) { onScopeChange(scope) }
                    .semantics { this.selected = selected }
                    .padding(horizontal = 14.dp)
                    .testTag("scope-${scope.name}"),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(scope.label()),
                    color = if (selected) MarginColors.Yellow else MarginColors.Ink,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

private fun SearchScope.label(): Int = when (this) {
    SearchScope.All -> R.string.search_scope_all
    SearchScope.Authors -> R.string.search_scope_authors
}

@Composable
private fun Message(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = MarginColors.Muted,
        fontSize = 14.sp,
        modifier = modifier.padding(start = 20.dp, end = 20.dp, top = 24.dp),
    )
}

@Composable
private fun Results(books: List<LibraryBook>, onBookClick: (String) -> Unit, contentPadding: PaddingValues) {
    LazyColumn(Modifier.fillMaxSize().testTag("search-results"), contentPadding = contentPadding) {
        item {
            Text(
                text = pluralStringResource(R.plurals.search_results, books.size, books.size).uppercase(),
                color = MarginColors.Muted,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.08.em,
                modifier = Modifier.padding(top = 22.dp, bottom = 6.dp).testTag("search-count"),
            )
        }
        itemsIndexed(books, key = { _, book -> book.id }) { index, book ->
            ResultRow(book, showDivider = index < books.lastIndex) { onBookClick(book.id) }
        }
    }
}

/** LIB-014: portada, título, autor y estado; tocarla abre el libro. */
@Composable
private fun ResultRow(book: LibraryBook, showDivider: Boolean, onClick: () -> Unit) {
    val status = statusText(book)
    val description = listOfNotNull(book.title, book.author, status).joinToString(". ")
    Column(
        Modifier
            .clickable(role = Role.Button, onClick = onClick)
            .clearAndSetSemantics { contentDescription = description; role = Role.Button }
            .testTag("result-${book.id}"),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            BookCover(book, Modifier.width(52.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(book.title, color = MarginColors.Ink, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                book.author?.let {
                    Text(it, color = MarginColors.Muted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    val reading = book.status == ReadingStatus.Reading
                    if (reading) Box(Modifier.size(6.dp).background(MarginColors.ReadingDot, CircleShape))
                    Text(
                        status,
                        color = if (reading) MarginColors.Ink else MarginColors.Muted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            Icon(SearchIcons.ChevronRight, contentDescription = null, tint = MarginColors.Ink, modifier = Modifier.size(22.dp))
        }
        if (showDivider) Box(Modifier.fillMaxWidth().height(1.dp).background(MarginColors.Line))
    }
}

/** "Leyendo · 42 %", "Nuevo" o "Terminado", y "En la nube" si no está en el dispositivo (LIB-007). */
@Composable
private fun statusText(book: LibraryBook): String {
    val reading = when (book.status) {
        ReadingStatus.Reading -> stringResource(R.string.search_status_reading, book.progressPercent ?: 0)
        ReadingStatus.New -> stringResource(R.string.library_status_new)
        ReadingStatus.Finished -> stringResource(R.string.library_status_finished)
    }
    val cloud = stringResource(R.string.library_status_cloud).takeIf { !book.isDownloaded }
    return listOfNotNull(reading, cloud).joinToString(" · ")
}

private val MAX_CONTENT_WIDTH = 640.dp
private val FieldShape = RoundedCornerShape(28.dp)
private val ChipShape = RoundedCornerShape(17.dp)
private val FieldText = TextStyle(fontFamily = HostGrotesk, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = MarginColors.Ink)

@Preview(showBackground = true, widthDp = 360, heightDp = 780)
@Composable
private fun SearchPreview() {
    SearchContent(
        query = "seneca",
        state = SearchUiState(
            loading = false,
            query = "seneca",
            results = listOf(
                LibraryBook("1", "Cartas a Lucilio", "Séneca", null, 42),
                LibraryBook("2", "Sobre la brevedad de la vida", "Séneca", null, null, isDownloaded = false),
                LibraryBook("3", "Vida de Séneca", "Pierre Grimal", null, 100),
            ),
        ),
        onQueryChange = {},
        onClear = {},
        onScopeChange = {},
        onBookClick = {},
    )
}
