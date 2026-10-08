package com.pluk.reader.ui.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pluk.reader.domain.model.ReadingTheme

/** RDR-013: controles que aparecen al tocar el centro de la pantalla: la barra superior y, debajo, el progreso. */
@Composable
fun ReaderControls(
    visible: Boolean,
    /** Si es true, al ocultarse los controles desaparecen de golpe, sin desvanecerse. */
    instantHide: Boolean,
    theme: ReadingTheme,
    progressPercent: Int?,
    chapterTitle: String,
    settingsOpen: Boolean,
    onBack: () -> Unit,
    onToggleSettings: () -> Unit,
) {
    AnimatedVisibility(visible = visible, enter = fadeIn(), exit = if (instantHide) ExitTransition.None else fadeOut(tween(80))) {
        Column(Modifier.statusBarsPadding()) {
            ReaderTopBar(
                theme = theme,
                chapterTitle = chapterTitle,
                settingsActive = settingsOpen,
                onBack = onBack,
                onToggleSettings = onToggleSettings,
            )
//            // RDR-005: el progreso sigue visible con los controles.
//            Text(
//                text = progressPercent?.let { "$it %" } ?: "",
//                color = theme.barMutedColor(),
//                fontSize = 11.sp,
//                fontWeight = FontWeight.Medium,
//                modifier = Modifier.fillMaxWidth().background(theme.pageBackground()).padding(start = 26.dp, bottom = 6.dp),
//            )
        }
    }
}

/** RDR-004: índice del libro. Se abre desde el panel de ajustes. */
@Composable
fun ReaderTocDialog(toc: List<TocEntry>, onDismiss: () -> Unit, onSelected: (TocEntry) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } },
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
                                .clickable { onSelected(entry) }
                                .padding(start = (entry.depth * 16).dp, top = 10.dp, bottom = 10.dp),
                        )
                    }
                }
            }
        },
    )
}

/** Lo que tarda en desaparecer la barra. El paso de página espera este tiempo para no capturarla (RDR-009). */
