package com.pluk.reader.ui.reader

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.widget.Toast
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.ui.platform.testTag
import androidx.compose.runtime.withFrameNanos
import kotlin.math.roundToInt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBarsIgnoringVisibility
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.layout.windowInsetsPadding
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
import com.pluk.reader.R
import com.pluk.reader.domain.model.LineSpacing
import com.pluk.reader.domain.model.ReaderFont
import com.pluk.reader.domain.model.ReaderSettings
import com.pluk.reader.domain.pageLabel
import com.pluk.reader.domain.useTwoPages
import com.pluk.reader.ui.theme.ReaderTheme
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.navigator.input.InputListener
import org.readium.r2.navigator.input.TapEvent
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Href
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.services.positions

@Composable
fun ReaderScreen(
    onBack: () -> Unit,
    viewModel: ReaderViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current

    // Enlaces externos del libro: solo web, en el navegador del sistema.
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is ReaderEvent.OpenExternalLink ->
                    if ((event.url.startsWith("http://") || event.url.startsWith("https://")) &&
                        !externalLinksBlockedForTests(context)
                    ) {
                        runCatching { uriHandler.openUri(event.url) }
                    }
                // RDR-012
                ReaderEvent.BodyEnded ->
                    Toast.makeText(context, R.string.reader_body_ended, Toast.LENGTH_LONG).show()
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
            publication = current.book.publication,
            controlsVisible = current.controlsVisible,
            chapterTitle = current.chapterTitle,
            settingsOpen = current.settingsOpen,
            onBack = onBack,
            actions = ReaderActions.of(viewModel),
        )
    }
}

@OptIn(ExperimentalReadiumApi::class, ExperimentalLayoutApi::class)
@Composable
private fun ReaderContent(
    settings: ReaderSettings,
    toc: List<TocEntry>,
    progressPercent: Int?,
    pageNumber: Int?,
    publication: Publication?,
    controlsVisible: Boolean,
    chapterTitle: String,
    settingsOpen: Boolean,
    onBack: () -> Unit,
    actions: ReaderActions,
) {
    var showToc by remember { mutableStateOf(false) }
    var navigator by remember { mutableStateOf<EpubNavigatorFragment?>(null) }
    var area by remember { mutableStateOf<Rect?>(null) }
    val scope = rememberCoroutineScope()
    val controller = remember(scope) { PageTurnController(scope) }
    val edges = remember { BookEdges<Locator>() }
    val window = LocalContext.current.findActivity()?.window

    // RDR-016, AND-006: dos páginas según el tamaño de la ventana (cambia al girar o redimensionar).
    val configuration = LocalConfiguration.current
    val twoPages = useTwoPages(configuration.screenWidthDp, configuration.screenHeightDp)
    var lastPosition by remember(publication) { mutableStateOf<Int?>(null) }
    LaunchedEffect(publication) { lastPosition = publication?.positions()?.size }

    // RDR-009: la animación se puede desactivar.
    val animated = settings.pageAnimation
    val outgoingTint = settings.theme.outgoingTint()
    val controlsVisible by rememberUpdatedState(controlsVisible)
    ImmersiveSystemBars(window, hidden = !controlsVisible)
    // Al pasar de página los controles se quitan de golpe: esperar a que se desvanezcan retrasa el inicio de la animación.
    var hideControlsInstantly by remember { mutableStateOf(false) }
    LaunchedEffect(controlsVisible) { if (controlsVisible) hideControlsInstantly = false }

    fun opsFor(nav: EpubNavigatorFragment, window: Window) = NavigatorPageTurnOps(
        navigator = nav,
        window = window,
        captureArea = { area?.toAndroidRect() },
        isReady = { !controlsVisible },
        edges = edges,
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
                when (PageTurnGesture.tapAction(event.point.x, width)) {
                    TapAction.PreviousPage -> currentTurnPage(PageTurnDirection.Backward)
                    TapAction.NextPage -> currentTurnPage(PageTurnDirection.Forward)
                    TapAction.ToggleControls -> actions.onToggleControls()
                }
                return true
            }
        })
        nav.currentLocator.collect { locator ->
            actions.onLocatorChanged(
                locator.toJSON().toString(),
                locator.locations.totalProgression,
                locator.locations.position,
                locator.href.toString(),
            )
        }
    }

    LaunchedEffect(navigator, settings, twoPages) {
        navigator?.submitPreferences(settings.toEpubPreferences(twoPages))
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

    val readingBackground = settings.theme.pageBackground()

    Box(
        Modifier
            .fillMaxSize()
            .background(readingBackground)
            .then(swipeModifier),
    ) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBarsIgnoringVisibility)) {
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
                            PreviewBookPage(settings.theme, twoPages, pageModifier)
                        } else {
                            AndroidFragment<EpubNavigatorFragment>(
                                pageModifier,
                                onUpdate = { fragment -> if (navigator !== fragment) navigator = fragment },
                            )
                        }
                        ReaderPageNumber(pageLabel(pageNumber, twoPages, lastPosition), settings.theme.pageTextColor())
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
                                // Cambia el fondo de la captura sin tocar el texto: así se distingue de la página de abajo.
                                colorFilter = ColorFilter.tint(
                                    outgoingTint.color.copy(alpha = transform.outgoingLightenAmount),
                                    outgoingTint.blendMode,
                                )
                            },
                    )
                }
            }
        }
        Box(Modifier.align(Alignment.TopCenter)) {
            ReaderControls(
                visible = controlsVisible,
                instantHide = hideControlsInstantly,
                theme = settings.theme,
                progressPercent = progressPercent,
                chapterTitle = chapterTitle,
                settingsOpen = settingsOpen,
                onBack = onBack,
                onToggleSettings = actions.onToggleSettings,
            )
        }
        // RDR-014: panel de ajustes sobre el libro. Cada acción se aplica al instante.
        if (settingsOpen) {
            ReaderSettingsSheet(
                settings = settings,
                actions = ReaderSettingsActions(
                    onDismiss = actions.onCloseSettings,
                    onFont = actions.onSetFont,
                    onSmallerFont = actions.onSmallerFont,
                    onBiggerFont = actions.onBiggerFont,
                    onTheme = actions.onSetTheme,
                    onLineSpacing = actions.onSetLineSpacing,
                    // RDR-004: abrir el índice cierra el panel.
                    onOpenToc = {
                        actions.onCloseSettings()
                        showToc = true
                    },
                    onTogglePageAnimation = actions.onTogglePageAnimation,
                ),
            )
        }
        if (showToc) {
            ReaderTocDialog(
                toc = toc,
                onDismiss = { showToc = false },
                onSelected = { entry ->
                    showToc = false
                    navigator?.go(entry.link, animated = false)
                },
            )
        }
    }
}

/** RDR-010: pie con la posición actual, debajo del texto y siempre visible en modo paginado. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReaderPageNumber(label: String?, color: Color) {
    Box(
        Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBarsIgnoringVisibility)
            .height(PAGE_NUMBER_HEIGHT)
        ,
        contentAlignment = Alignment.Center,
    ) {
        if (label != null) {
            Text(
                text = label,
                color = color.copy(alpha = 0.6f),
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.testTag(PAGE_NUMBER_TAG),
            )
        }
    }
}

// Colores de página de los temas de Readium, para que el pie se vea igual que el libro.
internal fun ReadingTheme.pageBackground() = when (this) {
    ReadingTheme.LIGHT -> Color(0xFFFFFFFF)
    ReadingTheme.DARK -> Color(0xFF000000)
    ReadingTheme.SEPIA -> Color(0xFFFAF4E8)
}

private fun ReadingTheme.pageTextColor() = when (this) {
    ReadingTheme.LIGHT, ReadingTheme.SEPIA -> Color(0xFF121212)
    ReadingTheme.DARK -> Color(0xFFFEFEFE)
}

/**
 * RDR-015: oculta las barras del sistema mientras los controles están ocultos. Los paddings del lector ignoran la
 * visibilidad de las barras, así el texto no se mueve. Al salir de la pantalla se vuelven a mostrar.
 */
@Composable
private fun ImmersiveSystemBars(window: Window?, hidden: Boolean) {
    if (window == null) return
    val view = LocalView.current
    LaunchedEffect(window, hidden) {
        val controller = WindowCompat.getInsetsController(window, view)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (hidden) controller.hide(WindowInsetsCompat.Type.systemBars())
        else controller.show(WindowInsetsCompat.Type.systemBars())
    }
    DisposableEffect(window) {
        onDispose { WindowCompat.getInsetsController(window, view).show(WindowInsetsCompat.Type.systemBars()) }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private fun androidx.compose.ui.geometry.Rect.toAndroidRect() =
    android.graphics.Rect(left.roundToInt(), top.roundToInt(), right.roundToInt(), bottom.roundToInt())

/** Fondo con el que sale la página en el tema oscuro (un poco más claro que el del libro). */
/**
 * Tinte que se le pone a la captura de la página que sale (RDR-009). En el tema oscuro aclara el fondo negro
 * hasta `#242728`; en claro y sepia lo oscurece un poco (multiplicando), porque no se puede aclarar un fondo claro.
 */
private class OutgoingTint(val color: Color, val blendMode: BlendMode)

private fun ReadingTheme.outgoingTint() = when (this) {
    ReadingTheme.DARK -> OutgoingTint(Color(0xFF242728), BlendMode.Screen)
    // Blanco → #E6E6E6
    ReadingTheme.LIGHT -> OutgoingTint(Color(0xFFE6E6E6), BlendMode.Multiply)
    // #FAF4E8 → #E6DCC5
    ReadingTheme.SEPIA -> OutgoingTint(Color(0xFFEBE6D9), BlendMode.Multiply)
}

private const val SNAPSHOT_TAG = "page-turn-snapshot"
private const val PAGE_NUMBER_TAG = "page-number"
private val PAGE_NUMBER_HEIGHT = 32.dp
/** Margen alrededor del texto de lectura. */
private val READING_PADDING = 24.dp

/** Espacio entre las dos páginas que deja Readium con SPREAD_PAGE_MARGINS; solo lo usa el preview. */
private val SPREAD_GAP = 100.dp

/** Acciones que el lector necesita de afuera. Separadas del `ViewModel` para poder previsualizar la pantalla. */
private class ReaderActions(
    val onToggleControls: () -> Unit,
    val onHideControls: () -> Unit,
    val onLocatorChanged: (json: String, totalProgression: Double?, position: Int?, href: String) -> Unit,
    val onSmallerFont: () -> Unit,
    val onBiggerFont: () -> Unit,
    val onTogglePageAnimation: () -> Unit,
    val onToggleSettings: () -> Unit,
    val onCloseSettings: () -> Unit,
    val onSetTheme: (ReadingTheme) -> Unit,
    val onSetFont: (ReaderFont) -> Unit,
    val onSetLineSpacing: (LineSpacing) -> Unit,
) {
    companion object {
        fun of(viewModel: ReaderViewModel) = ReaderActions(
            onToggleControls = viewModel::toggleControls,
            onHideControls = viewModel::hideControls,
            onLocatorChanged = { json, progression, position, href ->
                viewModel.onLocatorChanged(json, progression, position, href)
            },
            onSmallerFont = viewModel::smallerFont,
            onBiggerFont = viewModel::biggerFont,
            onTogglePageAnimation = viewModel::togglePageAnimation,
            onToggleSettings = viewModel::toggleSettings,
            onCloseSettings = viewModel::closeSettings,
            onSetTheme = viewModel::setTheme,
            onSetFont = viewModel::setFont,
            onSetLineSpacing = viewModel::setLineSpacing,
        )

        val None = ReaderActions({}, {}, { _, _, _, _ -> }, {}, {}, {}, {}, {}, {}, {}, {})
    }
}

/** Página de texto que hace de libro en el preview. */
@Composable
private fun PreviewBookPage(theme: ReadingTheme, twoPages: Boolean, modifier: Modifier = Modifier) {
    // RDR-016: con dos páginas, el texto se parte en dos columnas, como el navegador de Readium.
    val pages = if (twoPages) listOf(PREVIEW_PAGE_TEXT.take(PREVIEW_PAGE_TEXT.length / 2), PREVIEW_PAGE_TEXT.drop(PREVIEW_PAGE_TEXT.length / 2)) else listOf(PREVIEW_PAGE_TEXT)
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(SPREAD_GAP)) {
        pages.forEach { page ->
            Text(
                text = page,
                color = theme.pageTextColor(),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
        }
    }
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
            publication = null,
            controlsVisible = false,
            chapterTitle = "Libro II",
            settingsOpen = false,
            onBack = {},
            actions = ReaderActions.None,
        )
    }
}

// `device` (y no solo widthDp/heightDp) para que LocalConfiguration vea una ventana de tablet y se active RDR-016.
@Preview(
    showBackground = true,
    name = "Tablet 10\" horizontal (2 páginas)",
    device = "spec:width=1280dp,height=800dp,dpi=240",
)
@Composable
private fun ReaderScreenTabletLandscapePreview() {
    ReaderTheme(darkTheme = false, dynamicColor = false) {
        ReaderContent(
            settings = ReaderSettings(theme = ReadingTheme.SEPIA),
            toc = emptyList(),
            progressPercent = 42,
            pageNumber = 107,
            publication = null,
            controlsVisible = false,
            chapterTitle = "Libro II",
            settingsOpen = false,
            onBack = {},
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

/**
 * Solo en builds de depuración: si existe el archivo `block_external_links` en la carpeta de la app, los
 * enlaces externos no se abren. Sirve para probar a mano o con adb sin que un toque abra el navegador.
 * Se activa con `adb shell run-as com.pluk.reader touch files/block_external_links` y se quita con `rm`.
 */
private fun externalLinksBlockedForTests(context: Context): Boolean {
    val debuggable = context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0
    return debuggable && java.io.File(context.filesDir, "block_external_links").exists()
}
