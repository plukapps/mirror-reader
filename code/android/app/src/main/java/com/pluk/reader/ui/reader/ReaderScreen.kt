package com.pluk.reader.ui.reader

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.Image
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import android.view.Window
import androidx.compose.ui.geometry.Rect
import com.pluk.reader.domain.model.ReadingTheme
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.runtime.withFrameNanos
import kotlin.math.roundToInt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.background
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.fragment.compose.AndroidFragment
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pluk.reader.domain.model.OpenedBook
import com.pluk.reader.domain.model.ReaderSettings
import com.pluk.reader.ui.theme.ReaderTheme
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.navigator.input.InputListener
import org.readium.r2.navigator.input.TapEvent
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Href
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.Publication

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

        is ReaderUiState.Ready -> ReaderContent(
            settings = current.settings,
            toc = current.toc,
            progressPercent = current.progressPercent,
            pageNumber = current.pageNumber,
            controlsVisible = current.controlsVisible,
            actions = ReaderActions.of(viewModel),
        )
    }
}

@OptIn(ExperimentalReadiumApi::class)
@Composable
private fun ReaderContent(
    settings: ReaderSettings,
    toc: List<TocEntry>,
    progressPercent: Int?,
    pageNumber: Int?,
    controlsVisible: Boolean,
    actions: ReaderActions,
) {
    var navigator by remember { mutableStateOf<EpubNavigatorFragment?>(null) }
    var area by remember { mutableStateOf<Rect?>(null) }
    val scope = rememberCoroutineScope()
    val controller = remember(scope) { PageTurnController(scope) }
    val window = LocalContext.current.findActivity()?.window

    // La animación solo aplica en modo paginado (RDR-009).
    val animated = settings.pageAnimation && !settings.scroll
    val darkTheme = settings.theme == ReadingTheme.DARK
    val controlsVisible by rememberUpdatedState(controlsVisible)
    // Al pasar de página los controles se quitan de golpe: esperar a que se desvanezcan retrasa el inicio de la animación.
    var hideControlsInstantly by remember { mutableStateOf(false) }
    LaunchedEffect(controlsVisible) { if (controlsVisible) hideControlsInstantly = false }

    fun opsFor(nav: EpubNavigatorFragment, window: Window) = NavigatorPageTurnOps(
        navigator = nav,
        window = window,
        captureArea = { area?.toAndroidRect() },
        isReady = { !controlsVisible },
        beforeCapture = {
            // Los controles taparían la captura: se ocultan y se espera a que terminen de desvanecerse.
            if (controlsVisible) {
                hideControlsInstantly = true
                actions.onHideControls()
                repeat(3) { withFrameNanos { } }
            }
        },
    )

    // Pasa de página por un toque en el borde: animación completa, o inmediato si el efecto está desactivado.
    val turnPage: (PageTurnDirection) -> Unit = turn@{ direction ->
        val nav = navigator ?: return@turn
        if (animated && window != null) {
            if (controller.begin(direction, 0f, opsFor(nav, window))) controller.release(commit = true)
        } else if (!controller.busy) {
            NavigatorPageTurnOps(nav, window ?: return@turn, { null }, {}).moveWithoutAnimation(direction)
        }
    }
    val currentTurnPage by rememberUpdatedState(turnPage)
    val currentOpsFactory by rememberUpdatedState(::opsFor)

    // Cada instancia nueva del fragmento (primera vez o tras recrearse) se configura una vez.
    LaunchedEffect(navigator) {
        val nav = navigator ?: return@LaunchedEffect
        nav.addInputListener(object : InputListener {
            // Bordes: pasan de página. Centro (o cualquier punto en modo scroll): muestra u oculta los controles.
            override fun onTap(event: TapEvent): Boolean {
                val width = nav.requireView().width.toFloat()
                if (nav.overflow.value.scroll || width <= 0f) {
                    actions.onToggleControls()
                    return true
                }
                val edge = width * EDGE_FRACTION
                when {
                    event.point.x < edge -> currentTurnPage(PageTurnDirection.Backward)
                    event.point.x > width - edge -> currentTurnPage(PageTurnDirection.Forward)
                    else -> actions.onToggleControls()
                }
                return true
            }
        })
        nav.currentLocator.collect { locator ->
            actions.onLocatorChanged(
                locator.toJSON().toString(),
                locator.locations.totalProgression,
                locator.locations.position,
            )
        }
    }

    LaunchedEffect(navigator, settings) {
        navigator?.submitPreferences(settings.toEpubPreferences())
    }

    // El arrastre solo se intercepta cuando hay animación: si no, el deslizar es el nativo de Readium.
    val swipeModifier = if (animated && window != null) {
        Modifier.pointerInput(Unit) {
            detectPageSwipes(
                onDown = {
                    val nav = navigator
                    if (nav != null) controller.prefetch(currentOpsFactory(nav, window))
                },
                onStart = { direction, progress ->
                    val nav = navigator
                    nav != null && controller.begin(direction, progress, currentOpsFactory(nav, window))
                },
                onDrag = controller::update,
                onEnd = controller::release,
            )
        }
    } else {
        Modifier
    }

    // RDR-010: el pie reserva su propio espacio, fuera del texto, solo en modo paginado.
    val showPageNumber = !settings.scroll
    val readingBackground = settings.theme.pageBackground()

    Box(
        Modifier
            .fillMaxSize()
            .background(readingBackground)
            .then(swipeModifier),
    ) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .onGloballyPositioned { area = it.boundsInWindow() },
            ) {
                // Página que entra (el navegador en vivo). Al avanzar está debajo y se mueve un poco (paralaje).
                // Al retroceder va encima y entra deslizándose desde la izquierda (la animación al revés).
                Box(
                    Modifier
                        .fillMaxSize()
                        .zIndex(if (controller.direction == PageTurnDirection.Backward) 1f else 0f)
                        .graphicsLayer {
                            val transform = controller.transform(size.width)
                            translationX = transform.incomingTranslationX
                        }
                        // Fondo propio: el navegador es transparente y, al ir encima, dejaría ver la captura de abajo.
                        .background(readingBackground)
                ) {
                    // RDR-010: el número va dentro de la página, así se mueve (y se captura) con ella.
                    Column(Modifier.fillMaxSize()) {
                        val pageModifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(READING_PADDING, READING_PADDING * 2, READING_PADDING, READING_PADDING / 2)
                        if (LocalInspectionMode.current) {
                            // En el preview del IDE el navegador de Readium no se dibuja: una página de texto ocupa su lugar.
                            PreviewBookPage(settings.theme, pageModifier)
                        } else {
                            AndroidFragment<EpubNavigatorFragment>(
                                pageModifier,
                                onUpdate = { fragment -> if (navigator !== fragment) navigator = fragment },
                            )
                        }
                        if (showPageNumber) {
                            ReaderPageNumber(pageNumber, settings.theme.pageTextColor())
                        }
                    }
                }
                // Página que sale: captura con su fondo. Al avanzar va encima, deslizándose;
                // al retroceder queda debajo y se desplaza un poco (paralaje).
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
                                if (darkTheme) {
                                    // Aclara el fondo negro de la captura sin tocar el texto: así se distingue de la página de abajo.
                                    colorFilter = ColorFilter.tint(
                                        DARK_OUTGOING_BACKGROUND.copy(alpha = transform.outgoingLightenAmount),
                                        BlendMode.Screen,
                                    )
                                }
                            },
                    )
                }
            }
        }
        Box(Modifier.align(Alignment.TopCenter)) {
            ReaderControls(
                visible = controlsVisible,
                instantHide = hideControlsInstantly,
                settings = settings,
                progressPercent = progressPercent,
                toc = toc,
                onToggleScroll = actions.onToggleScroll,
                onNextTheme = actions.onNextTheme,
                onSmallerFont = actions.onSmallerFont,
                onBiggerFont = actions.onBiggerFont,
                onTogglePageAnimation = actions.onTogglePageAnimation,
                onTocSelected = { entry -> navigator?.go(entry.link, animated = false) },
            )
        }
    }
}

/** RDR-010: pie con la posición actual, debajo del texto y siempre visible en modo paginado. */
@Composable
private fun ReaderPageNumber(pageNumber: Int?, color: Color) {
    Box(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .height(PAGE_NUMBER_HEIGHT)
        ,
        contentAlignment = Alignment.Center,
    ) {
        if (pageNumber != null) {
            Text(
                text = pageNumber.toString(),
                color = color.copy(alpha = 0.6f),
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.testTag(PAGE_NUMBER_TAG),
            )
        }
    }
}

// Colores de página de los temas de Readium, para que el pie se vea igual que el libro.
private fun ReadingTheme.pageBackground() = when (this) {
    ReadingTheme.LIGHT -> Color(0xFFFFFFFF)
    ReadingTheme.DARK -> Color(0xFF000000)
    ReadingTheme.SEPIA -> Color(0xFFFAF4E8)
}

private fun ReadingTheme.pageTextColor() = when (this) {
    ReadingTheme.LIGHT, ReadingTheme.SEPIA -> Color(0xFF121212)
    ReadingTheme.DARK -> Color(0xFFFEFEFE)
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
/** Fondo con el que sale la página en el tema oscuro (un poco más claro que el del libro). */
private val DARK_OUTGOING_BACKGROUND = Color(0xFF242728)

private const val SNAPSHOT_TAG = "page-turn-snapshot"
private const val PAGE_NUMBER_TAG = "page-number"
private val PAGE_NUMBER_HEIGHT = 32.dp
/** Margen alrededor del texto de lectura. */
private val READING_PADDING = 24.dp

/** Acciones que el lector necesita de afuera. Separadas del `ViewModel` para poder previsualizar la pantalla. */
private class ReaderActions(
    val onToggleControls: () -> Unit,
    val onHideControls: () -> Unit,
    val onLocatorChanged: (json: String, totalProgression: Double?, position: Int?) -> Unit,
    val onToggleScroll: () -> Unit,
    val onNextTheme: () -> Unit,
    val onSmallerFont: () -> Unit,
    val onBiggerFont: () -> Unit,
    val onTogglePageAnimation: () -> Unit,
) {
    companion object {
        fun of(viewModel: ReaderViewModel) = ReaderActions(
            onToggleControls = viewModel::toggleControls,
            onHideControls = viewModel::hideControls,
            onLocatorChanged = { json, progression, position -> viewModel.onLocatorChanged(json, progression, position) },
            onToggleScroll = viewModel::toggleScroll,
            onNextTheme = viewModel::nextTheme,
            onSmallerFont = viewModel::smallerFont,
            onBiggerFont = viewModel::biggerFont,
            onTogglePageAnimation = viewModel::togglePageAnimation,
        )

        val None = ReaderActions({}, {}, { _, _, _ -> }, {}, {}, {}, {}, {})
    }
}

/** Página de texto que hace de libro en el preview. */
@Composable
private fun PreviewBookPage(theme: ReadingTheme, modifier: Modifier = Modifier) {
    Text(
        text = PREVIEW_PAGE_TEXT,
        color = theme.pageTextColor(),
        style = MaterialTheme.typography.bodyLarge,
        modifier = modifier,
    )
}

@Preview(showBackground = true, name = "Leyendo", widthDp = 360, heightDp = 720)
@Composable
private fun ReaderScreenReadingPreview() {
    ReaderTheme(darkTheme = false, dynamicColor = false) {
        ReaderContent(
            settings = ReaderSettings(theme = ReadingTheme.SEPIA),
            toc = emptyList(),
            progressPercent = 42,
            pageNumber = 123,
            controlsVisible = false,
            actions = ReaderActions.None,
        )
    }
}

private const val PREVIEW_PAGE_TEXT =
    "Llamadme Ismael. Hace algunos años —no importa cuántos exactamente—, teniendo poco o ningún dinero " +
        "en el bolsillo y nada que me interesara en particular en tierra, pensé que me iría a navegar un poco " +
        "para ver la parte líquida del mundo. Es una manera que tengo de ahuyentar la melancolía y regular la " +
        "circulación. Siempre que noto que se me forma una mueca amarga en la boca, siempre que en mi alma hay " +
        "un noviembre húmedo y lluvioso, entonces comprendo que es hora de hacerme a la mar cuanto antes un noviembre húmedo y lluvioso, entonces comprendo que es hora de hacerme a la mar cuanto antes un noviembre húmedo y lluvioso, entonces comprendo que es hora de hacerme a la mar cuanto antes un noviembre húmedo y lluvioso, entonces comprendo que es hora de hacerme a la mar cuanto antes un noviembre húmedo y lluvioso, entonces comprendo que es hora de hacerme a la mar cuanto antes un noviembre húmedo y lluvioso, entonces comprendo que es hora de hacerme a la mar cuanto antes un noviembre húmedo y lluvioso, entonces comprendo que es hora de hacerme a la mar cuanto antes."
