package com.pluk.reader.ui.reader

import com.pluk.reader.domain.model.ReaderSettings
import com.pluk.reader.domain.model.ReadingTheme
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ReaderControls(
    visible: Boolean,
    settings: ReaderSettings,
    progressPercent: Int?,
    toc: List<TocEntry>,
    onToggleScroll: () -> Unit,
    onNextTheme: () -> Unit,
    onSmallerFont: () -> Unit,
    onBiggerFont: () -> Unit,
    onTogglePageAnimation: () -> Unit,
    onTocSelected: (TocEntry) -> Unit,
) {
    var showToc by remember { mutableStateOf(false) }

    AnimatedVisibility(visible = visible, enter = fadeIn(), exit = fadeOut(tween(CONTROLS_FADE_OUT_MS))) {
        Surface(tonalElevation = 3.dp, modifier = Modifier.fillMaxWidth().statusBarsPadding()) {
            Column(modifier = Modifier.padding(horizontal = 8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = { showToc = true }) { Text("Índice") }
                    TextButton(onClick = onToggleScroll) { Text(if (settings.scroll) "Scroll" else "Páginas") }
                    TextButton(onClick = onNextTheme) {
                        Text(
                            when (settings.theme) {
                                ReadingTheme.LIGHT -> "Claro"
                                ReadingTheme.DARK -> "Oscuro"
                                ReadingTheme.SEPIA -> "Sepia"
                            },
                        )
                    }
                    TextButton(onClick = onSmallerFont) { Text("A−") }
                    TextButton(onClick = onBiggerFont) { Text("A+") }
                    TextButton(onClick = onTogglePageAnimation) {
                        Text(if (settings.pageAnimation) "Efecto" else "Sin efecto")
                    }
                }
                Text(
                    text = progressPercent?.let { "$it %" } ?: "",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(start = 12.dp, bottom = 6.dp),
                )
            }
        }
    }

    if (showToc) {
        AlertDialog(
            onDismissRequest = { showToc = false },
            confirmButton = { TextButton(onClick = { showToc = false }) { Text("Cerrar") } },
            title = { Text("Índice") },
            text = {
                if (toc.isEmpty()) {
                    Text("Este libro no tiene tabla de contenidos.")
                } else {
                    LazyColumn {
                        items(toc) { entry ->
                            Text(
                                text = entry.title,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        showToc = false
                                        onTocSelected(entry)
                                    }
                                    .padding(start = (entry.depth * 16).dp, top = 10.dp, bottom = 10.dp),
                            )
                        }
                    }
                }
            },
        )
    }
}

/** Lo que tarda en desaparecer la barra. El paso de página espera este tiempo para no capturarla (RDR-009). */
const val CONTROLS_FADE_OUT_MS = 80
