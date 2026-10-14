package com.pluk.reader.actions

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.click
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pluk.reader.domain.model.LibraryBook
import com.pluk.reader.ui.actions.BookAction
import com.pluk.reader.ui.actions.BookActionsMenu
import com.pluk.reader.ui.actions.BookActionsState
import com.pluk.reader.ui.actions.LocalBookActions
import com.pluk.reader.ui.actions.bookClickable
import com.pluk.reader.ui.actions.coverAnchor
import com.pluk.reader.ui.actions.rememberCoverAnchor
import com.pluk.reader.ui.library.BookCover
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BookActionsMenuTest {
    @get:Rule
    val compose = createComposeRule()

    private val state = BookActionsState()
    private val chosen = mutableListOf<BookAction>()
    private val opened = mutableListOf<String>()

    /** Una portada con mantener presionado y el menú en la raíz, como en `AppNavHost`, sin Hilt. */
    private fun show(book: LibraryBook) = compose.setContent {
        CompositionLocalProvider(LocalBookActions provides state) {
            Box(Modifier.fillMaxSize()) {
                val anchor = rememberCoverAnchor()
                Box(Modifier.testTag("cell").bookClickable(book, anchor) { opened += book.id }) {
                    BookCover(book, Modifier.width(104.dp).coverAnchor(anchor))
                }
                state.target?.let { target ->
                    BookActionsMenu(target, onAction = { chosen += it; state.close() }, onDismiss = state::close)
                }
            }
        }
    }

    private val dracula = LibraryBook("d", "Drácula", "Bram Stoker", null, null)

    // LIB-016, LIB-017: tocar abre el libro; mantener presionado muestra el menú con el estado
    @Test
    fun longPressShowsMenuWithStatusAndActions() {
        show(dracula)
        compose.onNodeWithTag("cell").performClick()
        assertEquals(listOf("d"), opened)

        compose.onNodeWithTag("cell").performTouchInput { longClick() }

        compose.onNodeWithText("Bram Stoker · Sin empezar").assertIsDisplayed()
        compose.onNodeWithTag("action-Read").assertIsDisplayed()
        compose.onNodeWithTag("action-Details").assertIsDisplayed()
        compose.onNodeWithTag("action-MarkFinished").assertIsDisplayed()
        compose.onNodeWithTag("action-ChangeCover").assertIsDisplayed()
        compose.onNodeWithTag("action-Remove").assertIsDisplayed()
    }

    // LIB-018: un libro terminado ofrece "Marcar como no leído"
    @Test
    fun finishedBookOffersMarkUnread() {
        show(dracula.copy(progressPercent = 100))
        compose.onNodeWithTag("cell").performTouchInput { longClick() }

        compose.onNodeWithTag("action-MarkUnread").performClick()

        assertEquals(listOf(BookAction.MarkUnread), chosen)
        assertNull(state.target)
    }

    // LIB-016: tocar fuera cierra sin hacer nada
    @Test
    fun tappingOutsideClosesWithoutAction() {
        show(dracula)
        compose.onNodeWithTag("cell").performTouchInput { longClick() }

        compose.onNodeWithTag("book-actions").performTouchInput { click(topLeft + Offset(5f, 5f)) }

        assertNull(state.target)
        assertEquals(emptyList<BookAction>(), chosen)
    }
}
