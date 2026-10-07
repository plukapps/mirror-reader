package com.pluk.reader.library

import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pluk.reader.domain.model.LibraryBook
import com.pluk.reader.domain.model.LibraryFilter
import com.pluk.reader.ui.library.LibraryContent
import com.pluk.reader.ui.library.LibraryUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LibraryScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val books = listOf(
        LibraryBook("a", "Meditaciones", "Marco Aurelio", null, 42),
        LibraryBook("b", "Drácula", null, null, null),
        LibraryBook("c", "Walden", null, null, 100),
    )

    private fun show(
        state: LibraryUiState,
        onFilter: (LibraryFilter) -> Unit = {},
        onBook: (String) -> Unit = {},
    ) = compose.setContent {
        LibraryContent(state, SnackbarHostState(), onFilter, onImport = {}, onBookClick = onBook)
    }

    // LIB-011: portada y estado de cada libro
    @Test
    fun showsEachBookWithItsStatus() {
        show(LibraryUiState(loading = false, books = books, allCount = 3, readingCount = 1, finishedCount = 1))
        compose.onNodeWithTag("book-a").assertIsDisplayed()
        compose.onNodeWithText("42 %").assertIsDisplayed()
        compose.onNodeWithText("Nuevo").assertIsDisplayed()
        compose.onNodeWithText("Terminado").assertIsDisplayed()
    }

    // LIB-010: pestañas con contador, y elegir una informa el filtro
    @Test
    fun tabsShowCountsAndReportSelection() {
        var selected: LibraryFilter? = null
        show(
            LibraryUiState(loading = false, books = books, allCount = 3, readingCount = 1, finishedCount = 1),
            onFilter = { selected = it },
        )
        compose.onNodeWithTag("tab-All").assertIsSelected()
        compose.onNodeWithText("Leyendo 1").assertIsDisplayed()
        compose.onNodeWithTag("tab-Reading").performClick()
        assertEquals(LibraryFilter.Reading, selected)
    }

    @Test
    fun tappingABookReportsItsId() {
        var opened: String? = null
        show(LibraryUiState(loading = false, books = books, allCount = 3), onBook = { opened = it })
        compose.onNodeWithTag("book-b").performClick()
        assertEquals("b", opened)
    }

    // LIB-001: biblioteca vacía invita a importar
    @Test
    fun emptyLibraryInvitesToImport() {
        show(LibraryUiState(loading = false))
        compose.onNodeWithTag("empty-library").assertIsDisplayed()
        compose.onNodeWithTag("import-button").assertIsDisplayed()
    }
}
