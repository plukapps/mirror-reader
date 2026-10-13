package com.pluk.reader.ui.actions

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pluk.reader.R
import com.pluk.reader.domain.model.LibraryBook
import com.pluk.reader.domain.model.ReadingStatus
import com.pluk.reader.ui.library.BookCover
import com.pluk.reader.ui.theme.LightMarginPalette
import com.pluk.reader.ui.theme.MarginColors
import kotlin.math.roundToInt

/** El libro cuyo menú está abierto y dónde está su portada en la pantalla (coordenadas de la raíz). */
data class BookActionsTarget(val book: LibraryBook, val coverBounds: Rect)

/** Estado del menú de un libro (LIB-016). Uno solo para toda la app: lo dibuja la raíz, encima de la barra inferior. */
@Stable
class BookActionsState {
    var target: BookActionsTarget? by mutableStateOf(null)
        private set

    fun open(book: LibraryBook, coverBounds: Rect) {
        target = BookActionsTarget(book, coverBounds)
    }

    fun close() {
        target = null
    }
}

/** Sin proveedor (vistas previas, pruebas de una pantalla sola), mantener presionado no hace nada. */
val LocalBookActions = staticCompositionLocalOf<BookActionsState?> { null }

/** Dónde quedó dibujada una portada, para levantarla sobre el velo al abrir el menú. */
class CoverAnchor {
    var bounds: Rect = Rect.Zero
}

@Composable
fun rememberCoverAnchor(): CoverAnchor = remember { CoverAnchor() }

/** Se pone en la portada que se levanta al abrir el menú. */
fun Modifier.coverAnchor(anchor: CoverAnchor): Modifier = onGloballyPositioned { anchor.bounds = it.boundsInRoot() }

/**
 * Reemplaza a `clickable` en todo lo que abre un libro: tocar lo abre y mantener presionado abre el menú del libro
 * (LIB-016). TalkBack ofrece "Más acciones" como acción de mantener presionado.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Modifier.bookClickable(book: LibraryBook, anchor: CoverAnchor, onClick: () -> Unit): Modifier {
    val actions = LocalBookActions.current
    val haptics = LocalHapticFeedback.current
    return combinedClickable(
        role = Role.Button,
        onClick = onClick,
        onLongClickLabel = stringResource(R.string.book_actions_more),
        onLongClick = actions?.let {
            {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                it.open(book, anchor.bounds)
            }
        },
    )
}

private val CardShape = RoundedCornerShape(20.dp)
private val CardWidth = 236.dp
private const val LiftedScale = 1.08f

/**
 * Menú de un libro según el diseño 05b: velo sobre toda la pantalla, la portada levantada en su lugar y la tarjeta
 * encima (o debajo si no entra). Tocar el velo o atrás lo cierra sin hacer nada.
 */
@Composable
fun BookActionsMenu(target: BookActionsTarget, onAction: (BookAction) -> Unit, onDismiss: () -> Unit) {
    BackHandler(onBack = onDismiss)
    val density = LocalDensity.current
    val safeTop = WindowInsets.statusBars.getTop(density)
    val safeBottom = WindowInsets.navigationBars.getBottom(density)
    val bounds = target.coverBounds
    Layout(
        modifier = Modifier.fillMaxSize().testTag("book-actions"),
        content = {
            Box(
                Modifier
                    .layoutId("scrim")
                    .fillMaxSize()
                    .background(LightMarginPalette.ink.copy(alpha = 0.55f)) // velo oscuro en los dos modos
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
            )
            BookCover(
                target.book,
                Modifier
                    .layoutId("cover")
                    .graphicsLayer { scaleX = LiftedScale; scaleY = LiftedScale }
                    .shadow(18.dp, RoundedCornerShape(8.dp), ambientColor = Color.Black, spotColor = Color.Black),
            )
            MenuCard(target.book, onAction, Modifier.layoutId("card"))
        },
    ) { measurables, constraints ->
        val scrim = measurables.first { it.layoutId == "scrim" }.measure(constraints)
        val cover = measurables.first { it.layoutId == "cover" }.measure(
            Constraints.fixed(bounds.width.roundToInt().coerceAtLeast(0), bounds.height.roundToInt().coerceAtLeast(0)),
        )
        val card = measurables.first { it.layoutId == "card" }.measure(Constraints(maxWidth = constraints.maxWidth))
        // La tarjeta se separa 14 dp de la portada ya agrandada.
        val lifted = bounds.inflate(bounds.height * (LiftedScale - 1f) / 2f)
        val position = placeMenu(
            anchor = lifted,
            menu = IntSize(card.width, card.height),
            container = IntSize(constraints.maxWidth, constraints.maxHeight),
            gap = 14.dp.roundToPx(),
            margin = 16.dp.roundToPx(),
            safeTop = safeTop,
            safeBottom = safeBottom,
        )
        layout(constraints.maxWidth, constraints.maxHeight) {
            scrim.place(0, 0)
            cover.place(bounds.left.roundToInt(), bounds.top.roundToInt())
            card.place(position)
        }
    }
}

@Composable
private fun MenuCard(book: LibraryBook, onAction: (BookAction) -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier
            .width(CardWidth)
            .shadow(24.dp, CardShape)
            .background(MarginColors.Field, CardShape),
    ) {
        Column(Modifier.padding(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(book.title, color = MarginColors.Ink, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(subtitle(book), color = MarginColors.Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        HorizontalDivider(color = MarginColors.Line, thickness = 1.dp)
        menuActions(book).forEach { action ->
            ActionRow(action, onClick = { onAction(action) })
            if (action == BookAction.ChangeCover) HorizontalDivider(color = MarginColors.Line, thickness = 1.dp)
        }
    }
}

@Composable
private fun subtitle(book: LibraryBook): String {
    val status = when (book.status) {
        ReadingStatus.New -> stringResource(R.string.book_actions_status_new)
        ReadingStatus.Finished -> stringResource(R.string.library_status_finished)
        ReadingStatus.Reading -> "${book.progressPercent} %"
    }
    return listOfNotNull(book.author, status).joinToString(" · ")
}

@Composable
private fun ActionRow(action: BookAction, onClick: () -> Unit) {
    val (icon, label) = when (action) {
        BookAction.Read -> R.drawable.ic_action_menu_book to R.string.book_actions_read
        BookAction.MarkFinished -> R.drawable.ic_action_check_circle to R.string.book_actions_mark_finished
        BookAction.MarkUnread -> R.drawable.ic_action_remove_done to R.string.book_actions_mark_unread
        BookAction.ChangeCover -> R.drawable.ic_action_add_photo_alternate to R.string.book_actions_change_cover
        BookAction.Remove -> R.drawable.ic_action_delete to R.string.book_actions_remove
    }
    val color = when (action) {
        BookAction.Remove -> MarginColors.Danger
        BookAction.Read -> MarginColors.OnYellow // sobre amarillo
        else -> MarginColors.Ink
    }
    Row(
        Modifier
            .fillMaxWidth()
            .height(50.dp)
            .then(if (action == BookAction.Read) Modifier.background(MarginColors.Yellow) else Modifier)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 18.dp)
            .testTag("action-${action.name}"),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = color, modifier = Modifier.size(22.dp))
        Text(stringResource(label), color = color, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }
}
