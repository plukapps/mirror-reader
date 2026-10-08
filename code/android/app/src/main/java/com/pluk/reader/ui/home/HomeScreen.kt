package com.pluk.reader.ui.home

import android.app.Activity
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.ui.text.style.TextDecoration
import com.pluk.reader.domain.model.LibraryFilter
import com.pluk.reader.ui.library.LibraryIcons
import java.time.Instant
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pluk.reader.R
import com.pluk.reader.domain.home.Greeting
import com.pluk.reader.domain.home.HomeContent
import com.pluk.reader.domain.home.greetingFor
import com.pluk.reader.domain.model.LibraryBook
import com.pluk.reader.ui.library.BookCover
import com.pluk.reader.ui.library.toText
import com.pluk.reader.ui.theme.MarginColors
import java.time.LocalTime

/** Pantalla de inicio (HOM-001 a HOM-004). */
@Composable
fun HomeScreen(
    onBookClick: (bookId: String) -> Unit,
    onSeeAll: (LibraryFilter?) -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state = viewModel.uiState.collectAsStateWithLifecycle().value
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    LaunchedEffect(viewModel) {
        viewModel.messages.collect { snackbar.showSnackbar(it.toText(context)) }
    }
    HomeContentView(
        loading = state.loading,
        content = state.content,
        greeting = remember { greetingFor(LocalTime.now().hour) },
        snackbar = snackbar,
        onImport = viewModel::onImport,
        onBookClick = onBookClick,
        onSeeAll = onSeeAll,
    )
}

/** Sin ViewModel, para poder probarla y previsualizarla. */
@Composable
fun HomeContentView(
    loading: Boolean,
    content: HomeContent,
    greeting: Greeting,
    snackbar: SnackbarHostState,
    onImport: (List<String>) -> Unit,
    onBookClick: (String) -> Unit,
    onSeeAll: (LibraryFilter?) -> Unit,
) {
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        onImport(uris.map { it.toString() })
    }
    Box(Modifier
        .fillMaxSize()
        .background(MarginColors.Paper), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier
                .widthIn(max = MAX_CONTENT_WIDTH)
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp),
        ) {
            Text(
                text = stringResource(greeting.text()),
                color = MarginColors.Ink,
                fontSize = 44.sp,
                lineHeight = 44.sp,
                letterSpacing = (-0.045).em,
                modifier = Modifier
                    .padding(start = 20.dp, end = 20.dp, top = 12.dp)
                    .testTag("home-greeting"),
            )
            if (!loading) {
                val reading = content.continueReading
                if (reading != null) {
                    ContinueCard(reading) { onBookClick(reading.id) }
                } else {
                    NothingReading(
                        libraryEmpty = content.libraryEmpty,
                        onAction = if (content.libraryEmpty) {
                            { picker.launch(arrayOf(EPUB_MIME_TYPE)) }
                        } else {
                            { onSeeAll(null) }
                        },
                    )
                }
                Sections(content, onBookClick, onSeeAll)
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }
}

private fun Greeting.text(): Int = when (this) {
    Greeting.Morning -> R.string.home_greeting_morning
    Greeting.Afternoon -> R.string.home_greeting_afternoon
    Greeting.Night -> R.string.home_greeting_night
}

@Composable
private fun ContinueCard(book: LibraryBook, onClick: () -> Unit) {
    val percent = book.progressPercent ?: 0
    val description = listOfNotNull(stringResource(R.string.home_continue), book.title, book.author, "$percent %")
        .joinToString(". ")
    Row(
        Modifier
            .padding(start = 20.dp, end = 20.dp, top = 24.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MarginColors.Ink)
            .clickable(role = Role.Button, onClick = onClick)
            .clearAndSetSemantics { contentDescription = description; role = Role.Button }
            .padding(16.dp)
            .testTag("continue-card"),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        BookCover(book, Modifier.width(96.dp))
        Column(Modifier
            .weight(1f)
            .heightIn(min = 144.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text(
                    stringResource(R.string.home_continue),
                    color = MarginColors.Yellow,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    book.title,
                    color = Color.White,
                    fontSize = 20.sp,
                    lineHeight = 22.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp),
                )
                book.author?.let {
                    Text(
                        it,
                        color = MarginColors.Line,
                        fontSize = 13.sp,
                        lineHeight = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
//                        modifier = Modifier.background(Color.Green),
                    )
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                LinearProgressIndicator(
                    progress = { percent / 100f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = MarginColors.Yellow,
                    trackColor = Color.White.copy(alpha = 0.2f),
                    strokeCap = StrokeCap.Round,
                    gapSize = 0.dp,
                    drawStopIndicator = {},
                )
                Text("$percent %", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun NothingReading(libraryEmpty: Boolean, onAction: () -> Unit) {
    Column(
        Modifier
            .padding(start = 20.dp, end = 20.dp, top = 24.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White)
            .padding(20.dp)
            .testTag("nothing-reading"),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(stringResource(R.string.home_nothing_reading_title), color = MarginColors.Ink, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        Text(stringResource(R.string.home_nothing_reading_body), color = MarginColors.Muted, fontSize = 14.sp)
        Row(
            Modifier
                .padding(top = 8.dp)
                .height(44.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(MarginColors.Ink)
                .clickable(role = Role.Button, onClick = onAction)
                .padding(horizontal = 20.dp)
                .testTag("nothing-reading-action"),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(if (libraryEmpty) R.string.library_import_first else R.string.home_go_library),
                color = MarginColors.Yellow,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
            )
        }
    }
}

@Composable
private fun Sections(content: HomeContent, onBookClick: (String) -> Unit, onSeeAll: (LibraryFilter?) -> Unit) {
    if (content.reading.isNotEmpty()) {
        BookRow(R.string.home_reading, content.readingCount, "reading-row", { onSeeAll(LibraryFilter.Reading) }) {
            items(content.reading, key = { it.id }) { ReadingItem(it, onBookClick) }
        }
    }
    if (content.recentlyAdded.isNotEmpty()) {
        BookRow(R.string.home_recently_added, content.recentlyAddedCount, "recent-row", { onSeeAll(LibraryFilter.All) }) {
            items(content.recentlyAdded, key = { it.id }) { RecentItem(it, onBookClick) }
        }
    }
    if (content.finished.isNotEmpty()) {
        BookRow(R.string.home_finished, content.finishedCount, "finished-row", { onSeeAll(LibraryFilter.Finished) }) {
            items(content.finished, key = { it.id }) { FinishedItem(it, onBookClick) }
        }
    }
}

/** Fila con título, total y "Ver todo" (HOM-008 a HOM-011). */
@Composable
private fun BookRow(
    @StringRes title: Int,
    count: Int,
    tag: String,
    onSeeAll: () -> Unit,
    content: LazyListScope.() -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 26.dp, bottom = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
            Text(stringResource(title), color = MarginColors.Ink, fontSize = 18.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.02).em)
            Text("$count", color = MarginColors.Muted, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        }
        Text(
            stringResource(R.string.home_see_all),
            color = MarginColors.Ink,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            textDecoration = TextDecoration.Underline,
            modifier = Modifier
                .clickable(role = Role.Button, onClick = onSeeAll)
                .padding(vertical = 8.dp)
                .testTag("$tag-see-all"),
        )
    }
    LazyRow(
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.testTag(tag),
        content = content,
    )
}

@Composable
private fun RowItem(
    book: LibraryBook,
    description: String,
    onClick: () -> Unit,
    cover: @Composable () -> Unit = { BookCover(book) },
    details: @Composable ColumnScope.() -> Unit,
) {
    Column(
        Modifier
            .width(104.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .clearAndSetSemantics { contentDescription = description; role = Role.Button }
            .testTag("home-book-${book.id}"),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        cover()
        Column(verticalArrangement = Arrangement.spacedBy(1.dp), content = details)
    }
}

@Composable
private fun TitleAndAuthor(book: LibraryBook, subtitle: String? = book.author) {
    Text(
        text = book.title,
        color = MarginColors.Ink,
        fontSize = 14.sp,
        lineHeight = 14.sp,
        fontWeight = FontWeight.SemiBold,
//        style = androidx.compose.ui.text.TextStyle(
//            fontSize = 13.sp,
//            fontWeight = FontWeight.SemiBold,
//            lineHeight = 13.sp,
//            platformStyle = androidx.compose.ui.text.PlatformTextStyle(includeFontPadding = false),
//        ),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
//        modifier = Modifier.background(Color.Red),
        )



    subtitle?.let {
        Text(
            text = it,
            color = MarginColors.Muted,
            fontSize = 12.sp,
            lineHeight = 12.sp,
//            style = androidx.compose.ui.text.TextStyle(
//                fontSize = 12.sp,
//                lineHeight = 12.sp,
//                platformStyle = androidx.compose.ui.text.PlatformTextStyle(includeFontPadding = false),
//            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
//            modifier = Modifier.background(Color.Blue),
            )
    }
}

@Composable
private fun ReadingItem(book: LibraryBook, onBookClick: (String) -> Unit) {
    val percent = book.progressPercent ?: 0
    val description = listOfNotNull(book.title, book.author, "$percent %").joinToString(". ")
    RowItem(book, description, onClick = { onBookClick(book.id) }) {
        TitleAndAuthor(book)
        Row(Modifier.padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LinearProgressIndicator(
                progress = { percent / 100f },
                modifier = Modifier
                    .weight(1f)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = MarginColors.Ink,
                trackColor = MarginColors.Line,
                strokeCap = StrokeCap.Round,
                gapSize = 0.dp,
                drawStopIndicator = {},
            )
            Text("$percent%", color = MarginColors.Muted, fontSize = 11.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun RecentItem(book: LibraryBook, onBookClick: (String) -> Unit) {
    val description = listOfNotNull(book.title, book.author).joinToString(". ")
    RowItem(book, description, onClick = { onBookClick(book.id) }) { TitleAndAuthor(book) }
}

@Composable
private fun FinishedItem(book: LibraryBook, onBookClick: (String) -> Unit) {
    val month = remember(book.lastReadAt) { book.lastReadAt?.let(::monthLabel) }
    val subtitle = listOfNotNull(book.author?.let(::shortAuthor), month).joinToString(" · ").ifEmpty { null }
    val description = listOfNotNull(book.title, book.author, stringResource(R.string.library_status_finished), month).joinToString(". ")
    RowItem(
        book,
        description,
        onClick = { onBookClick(book.id) },
        cover = {
            Box {
                BookCover(book)
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(MarginColors.Yellow),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(LibraryIcons.Check, contentDescription = null, tint = MarginColors.Ink, modifier = Modifier.size(15.dp))
                }
            }
        },
    ) { TitleAndAuthor(book, subtitle) }
}

/** "Mes" abreviado en el idioma del dispositivo, con mayúscula inicial (HOM-010). */
private fun monthLabel(epochMillis: Long): String {
    val locale = Locale.getDefault()
    return Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).month
        .getDisplayName(TextStyle.SHORT, locale).trimEnd('.').replaceFirstChar { it.titlecase(locale) }
}

/** Apellido del primer autor ("Peter Thiel, Blake Masters" → "Thiel"), como en el diseño: "Fitzgerald · Sep". */
private fun shortAuthor(author: String): String = author.substringBefore(",").trim().substringAfterLast(" ")

private const val EPUB_MIME_TYPE = "application/epub+zip"
private val MAX_CONTENT_WIDTH = 640.dp

@Preview(widthDp = 360, heightDp = 1100)
@Composable
private fun HomePreview() {
    val reading = listOf(
        LibraryBook("r1", "Crime and Punishment", "F. Dostoevsky", null, 64, 5),
        LibraryBook("r2", "Frankenstein", "Mary Shelley", null, 18, 4),
        LibraryBook("r3", "The Odyssey", "Homer", null, 33, 3),
    )
    val recent = listOf(
        LibraryBook("n1", "Pride and Prejudice", "Jane Austen", null, null),
        LibraryBook("n2", "Moby-Dick", "H. Melville", null, null),
        LibraryBook("n3", "Walden", "H. D. Thoreau", null, null),
    )
    val finished = listOf(
        LibraryBook("f1", "The Great Gatsby", "F. S. Fitzgerald", null, 100, 1_725_000_000_000),
        LibraryBook("f2", "Hamlet", "Shakespeare", null, 100, 1_722_000_000_000),
    )
    HomeContentView(
        loading = false,
        content = HomeContent(
            continueReading = LibraryBook("a", "Meditations", "Marcus Aurelius", null, 42, 9),
            reading = reading,
            readingCount = 5,
            recentlyAdded = recent,
            recentlyAddedCount = 24,
            finished = finished,
            finishedCount = 12,
            libraryEmpty = false,
        ),
        greeting = Greeting.Night,
        snackbar = remember { SnackbarHostState() },
        onImport = {},
        onBookClick = {},
        onSeeAll = {},
    )
}
