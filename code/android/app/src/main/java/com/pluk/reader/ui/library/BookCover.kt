package com.pluk.reader.ui.library

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.pluk.reader.domain.model.LibraryBook
import com.pluk.reader.ui.theme.MarginColors
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val CoverShape = RoundedCornerShape(6.dp)

/** Portada 2:3 (LIB-011). Sin portada en el EPUB se genera una con color y título. */
@Composable
fun BookCover(book: LibraryBook, modifier: Modifier = Modifier) {
    val bitmap by produceState<ImageBitmap?>(initialValue = null, book.coverPath) {
        value = book.coverPath?.let { path ->
            withContext(Dispatchers.IO) { BitmapFactory.decodeFile(path)?.asImageBitmap() }
        }
    }
    val frame = modifier
        .aspectRatio(2f / 3f)
        .clip(CoverShape)
        .border(3.dp, MarginColors.CoverBorder, CoverShape)
    val cover = bitmap
    if (cover != null) {
        // La descripción accesible la pone la celda de la grilla.
        Image(cover, contentDescription = null, modifier = frame, contentScale = ContentScale.Crop)
    } else {
        val background = MarginColors.Covers[abs(book.id.hashCode()) % MarginColors.Covers.size]
        Box(frame.background(background).padding(7.dp)) {
            Text(
                text = book.title,
                color = if (background.isLight()) MarginColors.Ink else Color.White,
                fontSize = 13.sp,
                lineHeight = 13.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-0.04).em,
                maxLines = 5,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun Color.isLight(): Boolean = (red * 0.299f + green * 0.587f + blue * 0.114f) > 0.6f
