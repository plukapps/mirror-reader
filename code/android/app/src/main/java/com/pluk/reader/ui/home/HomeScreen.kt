package com.pluk.reader.ui.home

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
    onOpenLibrary: () -> Unit,
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
        onOpenLibrary = onOpenLibrary,
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
    onOpenLibrary: () -> Unit,
) {
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        onImport(uris.map { it.toString() })
    }
    Box(Modifier.fillMaxSize().background(MarginColors.Paper), contentAlignment = Alignment.TopCenter) {
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
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp).testTag("home-greeting"),
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
                            onOpenLibrary
                        },
                    )
                }
                if (content.forYou.isNotEmpty()) ForYou(content.forYou, onBookClick, onOpenLibrary)
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
        Column(Modifier.weight(1f).heightIn(min = 144.dp), verticalArrangement = Arrangement.SpaceBetween) {
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
                book.author?.let { Text(it, color = MarginColors.Line, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                LinearProgressIndicator(
                    progress = { percent / 100f },
                    modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
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
private fun ForYou(books: List<LibraryBook>, onBookClick: (String) -> Unit, onSeeAll: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 28.dp, bottom = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(R.string.home_for_you), color = MarginColors.Ink, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text(
            stringResource(R.string.home_see_all),
            color = MarginColors.Ink,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .clickable(role = Role.Button, onClick = onSeeAll)
                .padding(vertical = 8.dp)
                .testTag("see-all"),
        )
    }
    LazyRow(
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.testTag("for-you"),
    ) {
        items(books, key = { it.id }) { book ->
            val description = listOfNotNull(book.title, book.author).joinToString(". ")
            Column(
                Modifier
                    .width(110.dp)
                    .clickable(role = Role.Button) { onBookClick(book.id) }
                    .clearAndSetSemantics { contentDescription = description; role = Role.Button }
                    .testTag("for-you-${book.id}"),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                BookCover(book)
                Text(book.title, color = MarginColors.Ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                book.author?.let { Text(it, color = MarginColors.Muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
        }
    }
}

private const val EPUB_MIME_TYPE = "application/epub+zip"
private val MAX_CONTENT_WIDTH = 640.dp

@Preview(widthDp = 360, heightDp = 780)
@Composable
private fun HomePreview() {
    HomeContentView(
        loading = false,
        content = HomeContent(
            continueReading = LibraryBook("a", "Meditations", "Marcus Aurelius", null, 42, 1),
            forYou = listOf(
                LibraryBook("b", "Pride & Prejudice", "Jane Austen", null, null),
                LibraryBook("c", "MOBY\n—DICK", "Herman Melville", null, null),
                LibraryBook("d", "Walden", "H. D. Thoreau", null, null),
            ),
            libraryEmpty = false,
        ),
        greeting = Greeting.Night,
        snackbar = remember { SnackbarHostState() },
        onImport = {},
        onBookClick = {},
        onOpenLibrary = {},
    )
}
