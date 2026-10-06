package com.pluk.reader.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ReaderControls(
    visible: Boolean,
    settings: ReaderSettings,
    onToggleScroll: () -> Unit,
    onNextTheme: () -> Unit,
    onSmallerFont: () -> Unit,
    onBiggerFont: () -> Unit,
) {
    AnimatedVisibility(visible = visible, enter = fadeIn(), exit = fadeOut()) {
        Surface(tonalElevation = 3.dp, modifier = Modifier.fillMaxWidth().statusBarsPadding()) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onToggleScroll) { Text(if (settings.scroll) "Scroll" else "Páginas") }
                TextButton(onClick = onNextTheme) {
                    Text(
                        when (settings.theme) {
                            ReaderTheme.LIGHT -> "Claro"
                            ReaderTheme.DARK -> "Oscuro"
                            ReaderTheme.SEPIA -> "Sepia"
                        },
                    )
                }
                TextButton(onClick = onSmallerFont) { Text("A−") }
                TextButton(onClick = onBiggerFont) { Text("A+") }
            }
        }
    }
}
