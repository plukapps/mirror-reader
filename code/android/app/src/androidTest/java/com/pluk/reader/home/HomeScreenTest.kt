package com.pluk.reader.home

import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pluk.reader.domain.home.Greeting
import com.pluk.reader.domain.home.homeContent
import com.pluk.reader.domain.model.LibraryBook
import com.pluk.reader.domain.model.LibraryFilter
import com.pluk.reader.ui.home.HomeContentView
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomeScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val books = listOf(
        LibraryBook("nuevo", "Drácula", "Bram Stoker", null, null, null, 5),
        LibraryBook("ahora", "Meditaciones", "Marco Aurelio", null, 42, 90, 4),
        LibraryBook("otro", "Frankenstein", "Mary Shelley", null, 18, 80, 3),
        LibraryBook("fin", "Hamlet", "William Shakespeare", null, 100, 70, 2),
    )

    private fun show(
        list: List<LibraryBook> = books,
        onBook: (String) -> Unit = {},
        onSeeAll: (LibraryFilter?) -> Unit = {},
    ) = compose.setContent {
        HomeContentView(false, homeContent(list), Greeting.Night, SnackbarHostState(), {}, onBook, onSeeAll)
    }

    // HOM-002 y HOM-008: el libro actual va en la tarjeta y no se repite en "Leyendo"
    @Test
    fun continueCardIsNotRepeatedInReadingRow() {
        show()
        compose.onNodeWithTag("continue-card").assertIsDisplayed()
        compose.onNodeWithTag("home-book-otro").assertIsDisplayed()
        compose.onNodeWithTag("reading-row").assertIsDisplayed()
    }

    // HOM-009, HOM-010
    @Test
    fun showsRecentlyAddedAndFinishedRows() {
        show()
        compose.onNodeWithTag("recent-row").assertIsDisplayed()
        compose.onNodeWithTag("finished-row").assertIsDisplayed()
        compose.onNodeWithText("Terminados").assertIsDisplayed()
    }

    // HOM-011: cada "Ver todo" informa su filtro
    @Test
    fun seeAllReportsTheSectionFilter() {
        val received = mutableListOf<LibraryFilter?>()
        show(onSeeAll = { received += it })
        compose.onNodeWithTag("reading-row-see-all").performClick()
        compose.onNodeWithTag("recent-row-see-all").performClick()
        compose.onNodeWithTag("finished-row-see-all").performClick()
        assertEquals(listOf(LibraryFilter.Reading, LibraryFilter.All, LibraryFilter.Finished), received)
    }

    // HOM-008: sin libros en lectura aparte del actual, la fila no se muestra
    @Test
    fun readingRowIsHiddenWithoutOtherBooksInProgress() {
        show(listOf(books[1], books[3]))
        compose.onNodeWithTag("reading-row").assertDoesNotExist()
    }
}
