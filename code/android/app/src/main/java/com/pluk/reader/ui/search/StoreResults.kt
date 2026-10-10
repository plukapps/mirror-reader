package com.pluk.reader.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.pluk.reader.R
import com.pluk.reader.domain.store.Acquisition
import com.pluk.reader.domain.store.StoreBook
import com.pluk.reader.domain.store.StoreResult
import com.pluk.reader.ui.library.BookCover
import com.pluk.reader.ui.theme.MarginColors
import java.util.Locale

/** Título de la parte del catálogo y su estado: buscando, sin conexión o sin resultados (STO-001, STO-007). */
@Composable
internal fun StoreHeader(status: StoreStatus, isEmpty: Boolean) {
    Column(Modifier.padding(top = 22.dp, bottom = 6.dp)) {
        Text(
            text = stringResource(R.string.store_section).uppercase(),
            color = MarginColors.Muted,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.08.em,
        )
        val message = when (status) {
            StoreStatus.Loading -> stringResource(R.string.store_loading)
            StoreStatus.Unavailable -> stringResource(R.string.store_unavailable)
            StoreStatus.Ready -> if (isEmpty) stringResource(R.string.store_no_results) else null
            StoreStatus.Idle -> null
        }
        if (message != null) {
            Row(
                Modifier.padding(top = 10.dp).testTag("store-status-${status.name}"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (status == StoreStatus.Loading) CircularProgressIndicator(Modifier.size(14.dp), color = MarginColors.Muted, strokeWidth = 2.dp)
                Text(message, color = MarginColors.Muted, fontSize = 14.sp)
            }
        }
    }
}

/**
 * Resultado del catálogo (STO-002, STO-003, STO-008): portada, título, autor, cómo obtenerlo y el
 * botón. Si ya está en la biblioteca, tocarlo abre el libro.
 */
@Composable
internal fun StoreRow(
    result: StoreResult,
    itemState: StoreItemState?,
    showDivider: Boolean,
    onOpen: (bookId: String) -> Unit,
    onGet: (StoreBook) -> Unit,
    onBuy: (StoreBook) -> Unit,
) {
    val book = result.book
    val libraryBook = result.inLibrary
    val deliveredId = libraryBook?.id ?: (itemState as? StoreItemState.Delivered)?.bookId
    val inLibrary = stringResource(R.string.store_in_library)
    val status = when {
        libraryBook != null -> "$inLibrary · ${statusText(libraryBook)}"
        deliveredId != null -> inLibrary
        itemState == StoreItemState.Downloading -> stringResource(R.string.store_downloading)
        itemState is StoreItemState.Failed -> itemState.message
        else -> when (val acquisition = book.acquisition) {
            is Acquisition.Free -> stringResource(R.string.store_free)
            is Acquisition.Paid -> stringResource(R.string.store_test_price, formatPrice(acquisition.priceCents))
            Acquisition.Unavailable -> stringResource(R.string.store_unavailable_digital)
        }
    }
    val description = listOfNotNull(book.title, book.author, status).joinToString(". ")
    Column(
        Modifier
            .then(if (deliveredId != null) Modifier.clickable(role = Role.Button) { onOpen(deliveredId) } else Modifier)
            .testTag("store-${book.key}"),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (libraryBook != null) BookCover(libraryBook, Modifier.width(52.dp)) else StoreCover(book, Modifier.width(52.dp))
            Column(
                Modifier.weight(1f).semantics(mergeDescendants = true) { contentDescription = description },
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(book.title, color = MarginColors.Ink, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                book.author?.let {
                    Text(it, color = MarginColors.Muted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(
                    status,
                    color = if (itemState is StoreItemState.Failed) StoreError else if (deliveredId != null) MarginColors.Ink else MarginColors.Muted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                )
            }
            when {
                deliveredId != null ->
                    Icon(SearchIcons.ChevronRight, contentDescription = null, tint = MarginColors.Ink, modifier = Modifier.size(22.dp))
                itemState == StoreItemState.Downloading ->
                    CircularProgressIndicator(Modifier.size(22.dp).testTag("store-progress"), color = MarginColors.Ink, strokeWidth = 2.dp)
                else -> when (book.acquisition) {
                    is Acquisition.Free -> RowButton(
                        text = stringResource(if (itemState is StoreItemState.Failed) R.string.store_retry else R.string.store_get),
                        tag = "store-get-${book.key}",
                    ) { onGet(book) }
                    is Acquisition.Paid -> RowButton(
                        text = stringResource(if (itemState is StoreItemState.Failed) R.string.store_retry else R.string.store_buy),
                        tag = "store-buy-${book.key}",
                    ) { onBuy(book) }
                    Acquisition.Unavailable -> Unit
                }
            }
        }
        if (showDivider) Box(Modifier.fillMaxWidth().height(1.dp).background(MarginColors.Line))
    }
}

/** Botón "Obtener" del diseño 03: 32 dp, borde de 1,5 dp tinta, 12 sp semibold. */
@Composable
private fun RowButton(text: String, tag: String, onClick: () -> Unit) {
    Box(
        Modifier
            .height(32.dp)
            .clip(RoundedCornerShape(16.dp))
            .border(1.5.dp, MarginColors.Ink, RoundedCornerShape(16.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp)
            .testTag(tag),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = MarginColors.Ink, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** Error, como en el onboarding. */
private val StoreError = Color(0xFF8B1E1E)

/** "US$ 4,99". */
internal fun formatPrice(cents: Int): String = String.format(Locale.forLanguageTag("es"), "US$ %.2f", cents / 100.0)

/** Compra simulada (STO-004): portada, título, autor, precio y que no se cobra nada. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PurchaseSheet(book: StoreBook, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val price = (book.acquisition as? Acquisition.Paid)?.priceCents?.let(::formatPrice) ?: return
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MarginColors.Paper,
        contentColor = MarginColors.Ink,
        scrimColor = Color.Black.copy(alpha = 0.2f),
        modifier = Modifier.testTag("purchase-sheet"),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp).navigationBarsPadding().padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                StoreCover(book, Modifier.width(64.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(book.title, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    book.author?.let { Text(it, color = MarginColors.Muted, fontSize = 14.sp) }
                    Text(price, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
                }
            }
            Text(
                stringResource(R.string.store_purchase_simulated),
                color = MarginColors.Muted,
                fontSize = 13.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MarginColors.Field, RoundedCornerShape(12.dp))
                    .padding(12.dp),
            )
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .clip(CircleShape)
                    .background(MarginColors.Ink)
                    .clickable(role = Role.Button, onClick = onConfirm)
                    .testTag("purchase-confirm"),
                contentAlignment = Alignment.Center,
            ) {
                Text(stringResource(R.string.store_purchase_confirm, price), color = MarginColors.Yellow, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
            Text(
                stringResource(R.string.store_purchase_cancel),
                color = MarginColors.Ink,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(CircleShape)
                    .clickable(role = Role.Button, onClick = onDismiss)
                    .padding(vertical = 12.dp),
            )
        }
    }
}
