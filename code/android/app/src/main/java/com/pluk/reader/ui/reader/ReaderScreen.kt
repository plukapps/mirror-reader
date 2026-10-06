package com.pluk.reader.ui.reader

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.Image
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.readium.r2.navigator.preferences.ReadingProgression
import kotlin.math.roundToInt
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
    var area by remember { mutableStateOf<Rect?>(null) }
    val controller = remember { PageTurnController() }
    val scope = rememberCoroutineScope()
    val window = LocalContext.current.findActivity()?.window

    // La animación solo aplica en modo paginado (RDR-009).
    val animated = state.settings.pageAnimation && !state.settings.scroll

    val turnPage: (PageTurnDirection) -> Unit = turn@{ direction ->
        val nav = navigator ?: return@turn
        if (controller.busy) return@turn
        scope.launch {
            fun move() = if (direction == PageTurnDirection.Forward) nav.goForward(animated = false) else nav.goBackward(animated = false)
            val captureArea = area
            if (!animated || window == null || captureArea == null) {
                move()
                return@launch
            }
            if (state.controlsVisible) {
                // Los controles taparían la captura: se ocultan antes de tomarla.
                viewModel.hideControls()
                delay(CONTROLS_FADE_OUT_MS + 40L)
                repeat(2) { withFrameNanos { } }
            }
            val before = nav.currentLocator.value
            controller.run(
                direction = direction,
                capture = { captureWindowArea(window, captureArea.toAndroidRect()) },
                navigate = ::move,
                awaitNavigated = {
                    withTimeoutOrNull(NAVIGATION_TIMEOUT_MS) { nav.currentLocator.first { it != before } }
                    repeat(2) { withFrameNanos { } }
                },
            )
        }
    }
    val currentTurnPage by rememberUpdatedState(turnPage)

    // Cada instancia nueva del fragmento (primera vez o tras recrearse) se configura una vez.
    LaunchedEffect(navigator) {
        val nav = navigator ?: return@LaunchedEffect
        nav.addInputListener(object : InputListener {
            // Bordes: pasan de página. Centro (o cualquier punto en modo scroll): muestra u oculta los controles.
            override fun onTap(event: TapEvent): Boolean {
                val width = nav.requireView().width.toFloat()
                if (nav.overflow.value.scroll || width <= 0f) {
                    viewModel.toggleControls()
                    return true
                }
                val rtl = nav.overflow.value.readingProgression == ReadingProgression.RTL
                val edge = width * EDGE_FRACTION
                when {
                    event.point.x < edge -> currentTurnPage(if (rtl) PageTurnDirection.Forward else PageTurnDirection.Backward)
                    event.point.x > width - edge -> currentTurnPage(if (rtl) PageTurnDirection.Backward else PageTurnDirection.Forward)
                    else -> viewModel.toggleControls()
                }
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

    val swipeModifier = if (animated) {
        Modifier.pointerInput(Unit) {
            detectPageSwipes { swipe ->
                val rtl = navigator?.overflow?.value?.readingProgression == ReadingProgression.RTL
                currentTurnPage(
                    if (!rtl) swipe else if (swipe == PageTurnDirection.Forward) PageTurnDirection.Backward else PageTurnDirection.Forward,
                )
            }
        }
    } else {
        Modifier
    }

    Box(
        Modifier
            .fillMaxSize()
            .onGloballyPositioned { area = it.boundsInWindow() }
            .then(swipeModifier),
    ) {
        // Página que entra: está debajo y se mueve un poco (paralaje).
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { translationX = controller.transform(size.width).incomingTranslationX },
        ) {
            AndroidFragment<EpubNavigatorFragment>(
                modifier = Modifier.fillMaxSize(),
                onUpdate = { fragment -> if (navigator !== fragment) navigator = fragment },
            )
        }
        // Página que sale: captura con su fondo, encima, deslizándose con menos opacidad.
        controller.snapshot?.let { snapshot ->
            Image(
                bitmap = snapshot,
                contentDescription = null,
                contentScale = ContentScale.FillBounds,
                modifier = Modifier
                    .fillMaxSize()
                    .testTag(SNAPSHOT_TAG)
                    .graphicsLayer {
                        val transform = controller.transform(size.width)
                        translationX = transform.outgoingTranslationX
                        alpha = transform.outgoingAlpha
                    },
            )
        }
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
                onTogglePageAnimation = viewModel::togglePageAnimation,
                onTocSelected = { entry -> navigator?.go(entry.link, animated = false) },
            )
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private fun androidx.compose.ui.geometry.Rect.toAndroidRect() =
    android.graphics.Rect(left.roundToInt(), top.roundToInt(), right.roundToInt(), bottom.roundToInt())

/** Fracción del ancho, a cada lado, donde un toque pasa de página. */
private const val EDGE_FRACTION = 0.3f
private const val NAVIGATION_TIMEOUT_MS = 400L
private const val SNAPSHOT_TAG = "page-turn-snapshot"
