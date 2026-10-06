package com.pluk.reader.ui.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.fragment.compose.AndroidFragment
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.navigator.input.InputListener
import org.readium.r2.navigator.input.TapEvent
import org.readium.r2.navigator.util.DirectionalNavigationAdapter
import org.readium.r2.shared.ExperimentalReadiumApi

@Composable
fun ReaderScreen(
    onBack: () -> Unit,
    viewModel: ReaderViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current

    // Enlaces externos del libro: solo web, en el navegador del sistema.
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            if (event is ReaderEvent.OpenExternalLink &&
                (event.url.startsWith("http://") || event.url.startsWith("https://"))
            ) {
                runCatching { uriHandler.openUri(event.url) }
            }
        }
    }

    when (val current = state) {
        ReaderUiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }

        is ReaderUiState.Failed -> Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(current.message)
            Button(onClick = onBack, modifier = Modifier.padding(top = 16.dp)) { Text("Volver") }
        }

        is ReaderUiState.Ready -> ReaderContent(current, viewModel)
    }
}

@OptIn(ExperimentalReadiumApi::class)
@Composable
private fun ReaderContent(state: ReaderUiState.Ready, viewModel: ReaderViewModel) {
    var navigator by remember { mutableStateOf<EpubNavigatorFragment?>(null) }

    // Cada instancia nueva del fragmento (primera vez o tras recrearse) se configura una vez.
    LaunchedEffect(navigator) {
        val nav = navigator ?: return@LaunchedEffect
        // Toques en los bordes pasan de página. Un toque en el centro muestra u oculta los controles.
        nav.addInputListener(DirectionalNavigationAdapter(nav))
        nav.addInputListener(object : InputListener {
            override fun onTap(event: TapEvent): Boolean {
                viewModel.toggleControls()
                return true
            }
        })
        nav.currentLocator.collect { locator ->
            viewModel.onLocatorChanged(locator.toJSON().toString(), locator.locations.totalProgression)
        }
    }

    LaunchedEffect(navigator, state.settings) {
        navigator?.submitPreferences(state.settings.toEpubPreferences())
    }

    Box(Modifier.fillMaxSize()) {
        AndroidFragment<EpubNavigatorFragment>(
            modifier = Modifier.fillMaxSize(),
            onUpdate = { fragment -> if (navigator !== fragment) navigator = fragment },
        )
        Box(Modifier.align(Alignment.TopCenter)) {
            ReaderControls(
                visible = state.controlsVisible,
                settings = state.settings,
                progressPercent = state.progressPercent,
                toc = state.toc,
                onToggleScroll = viewModel::toggleScroll,
                onNextTheme = viewModel::nextTheme,
                onSmallerFont = viewModel::smallerFont,
                onBiggerFont = viewModel::biggerFont,
                onTocSelected = { entry -> navigator?.go(entry.link, animated = false) },
            )
        }
    }
}
