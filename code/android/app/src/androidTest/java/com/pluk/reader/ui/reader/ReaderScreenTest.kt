package com.pluk.reader.ui.reader

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.pluk.reader.domain.model.ReadingTheme
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.fragment.app.FragmentContainerView
import androidx.fragment.app.findFragment
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pluk.reader.MainActivity
import com.pluk.reader.data.library.copyWithSha256
import com.pluk.reader.domain.repository.PositionRepository
import com.pluk.reader.domain.repository.SettingsRepository
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.readium.r2.navigator.preferences.Theme
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.util.Url
import org.readium.r2.shared.util.mediatype.MediaType
import java.io.ByteArrayOutputStream
import java.io.File
import javax.inject.Inject

@OptIn(ExperimentalReadiumApi::class)
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class ReaderScreenTest {
    @get:Rule(order = 0)
    val hilt = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val compose = createEmptyComposeRule()

    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var positionRepository: PositionRepository

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context: Context = instrumentation.targetContext
    // El libro se identifica por el hash de su contenido (LIB-003).
    private val bookId: String by lazy {
        instrumentation.context.assets.open("minimal.epub").use { copyWithSha256(it, ByteArrayOutputStream()) }
    }

    @Before
    fun setUp() = hilt.inject()

    private fun copyAsset(name: String): Uri {
        val file = File(context.cacheDir, name)
        instrumentation.context.assets.open(name).use { input ->
            file.outputStream().use { input.copyTo(it) }
        }
        return Uri.fromFile(file)
    }

    private fun launch(uri: Uri? = null): ActivityScenario<MainActivity> {
        val intent = Intent(context, MainActivity::class.java).apply { data = uri }
        return ActivityScenario.launch(intent)
    }

    // El reloj de Compose en pruebas solo avanza al sincronizar: sin waitForIdle la UI nunca se compone.
    private fun waitUntil(timeoutMs: Long = 15_000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            compose.waitForIdle()
            if (condition()) return
            Thread.sleep(100)
        }
        throw AssertionError("Condición no cumplida en ${timeoutMs}ms")
    }

    // AndroidFragment aloja el fragmento en su propio FragmentContainerView, no en la lista del
    // FragmentManager de la actividad: se busca recorriendo las vistas.
    private fun navigator(scenario: ActivityScenario<MainActivity>): EpubNavigatorFragment? {
        var found: EpubNavigatorFragment? = null
        scenario.onActivity { activity ->
            fun search(view: View): EpubNavigatorFragment? {
                if (view is FragmentContainerView && view.childCount > 0) {
                    runCatching { view.getChildAt(0).findFragment<EpubNavigatorFragment>() }
                        .getOrNull()?.let { return it }
                }
                if (view is ViewGroup) {
                    for (i in 0 until view.childCount) search(view.getChildAt(i))?.let { return it }
                }
                return null
            }
            found = search(activity.window.decorView)
        }
        return found
    }

    private fun currentHref(scenario: ActivityScenario<MainActivity>): String =
        navigator(scenario)?.currentLocator?.value?.href?.toString().orEmpty()

    // RDR-007, AND-003: "Abrir con" lleva directo al lector
    @Test
    fun viewIntentOpensTheReader() {
        launch(copyAsset("minimal.epub")).use { scenario ->
            waitUntil { navigator(scenario) != null }
        }
    }

    // La biblioteca es la pantalla de inicio (LIB-001)
    @Test
    fun withoutBookShowsLibrary() {
        launch().use {
            compose.onNodeWithText("Biblioteca").assertIsDisplayed()
        }
    }

    // Foco de revisión 2: rotación o recreación en plena lectura
    @Test
    fun keepsReaderAfterRecreation() {
        launch(copyAsset("minimal.epub")).use { scenario ->
            waitUntil { navigator(scenario) != null }
            scenario.recreate()
            waitUntil { navigator(scenario) != null }
            compose.onNodeWithText("Índice").assertIsDisplayed()
        }
    }

    // LIB-002, foco de revisión 1: archivo que no es EPUB
    @Test
    fun showsErrorMessageForCorruptFile() {
        val file = File(context.cacheDir, "corrupto.epub").apply { writeText("esto no es un epub") }
        launch(Uri.fromFile(file)).use { scenario ->
            waitUntil {
                runCatching {
                    compose.onNodeWithText("No se pudo", substring = true).assertIsDisplayed()
                }.isSuccess
            }
            assertNull(navigator(scenario))
        }
    }

    // RDR-008 (local): los ajustes guardados se aplican al abrir el libro
    @Test
    fun savedSettingsAreAppliedWhenOpeningBook() {
        runBlocking { settingsRepository.update { it.withTheme(ReadingTheme.SEPIA) } }
        launch(copyAsset("minimal.epub")).use { scenario ->
            waitUntil { navigator(scenario)?.settings?.value?.theme == Theme.SEPIA }
        }
    }

    // RDR-001: la lectura es siempre paginada
    @Test
    fun readingIsAlwaysPaged() {
        launch(copyAsset("minimal.epub")).use { scenario ->
            waitUntil { navigator(scenario) != null }
            assertEquals(false, navigator(scenario)?.settings?.value?.scroll)
        }
    }

    // RDR-004: el índice lista los capítulos y navega al elegido
    @Test
    fun tocDialogListsChaptersAndNavigates() {
        launch(copyAsset("minimal.epub")).use { scenario ->
            waitUntil { navigator(scenario) != null }
            // RDR-014: el índice se abre desde el panel de ajustes ("Aa" → "Abrir").
            compose.onNodeWithTag("reader-settings-button").performClick()
            compose.onNodeWithTag("reader-toc-button").performClick()
            compose.onNodeWithText("Capítulo 1").assertIsDisplayed()
            compose.onNodeWithText("Capítulo 2").assertIsDisplayed().performClick()
            waitUntil { currentHref(scenario).endsWith("ch2.xhtml") }
        }
    }

    // RDR-005: el progreso se muestra
    @Test
    fun progressIsShown() {
        launch(copyAsset("minimal.epub")).use { scenario ->
            waitUntil { navigator(scenario) != null }
            waitUntil {
                runCatching { compose.onNodeWithText("%", substring = true).assertIsDisplayed() }.isSuccess
            }
        }
    }

    // RDR-010: el pie muestra el número de página
    @Test
    fun pageNumberIsShown() {
        launch(copyAsset("minimal.epub")).use { scenario ->
            waitUntil { navigator(scenario) != null }
            waitUntil {
                runCatching { compose.onNodeWithTag("page-number").assertIsDisplayed() }.isSuccess
            }
        }
    }

    // Foco de revisión 4: cerrar y reabrir vuelve a la misma posición (RDR-006)
    @Test
    fun reopeningBookRestoresPosition() {
        val uri = copyAsset("minimal.epub")
        launch(uri).use { scenario ->
            waitUntil { navigator(scenario) != null }
            scenario.onActivity {
                navigator(scenario)!!.go(
                    Locator(href = Url("OEBPS/ch2.xhtml")!!, mediaType = MediaType.XHTML),
                    animated = false,
                )
            }
            waitUntil {
                runBlocking { positionRepository.get(bookId) }?.contains("ch2.xhtml") == true
            }
        }
        launch(uri).use { scenario ->
            waitUntil { currentHref(scenario).endsWith("ch2.xhtml") }
        }
    }

    // RDR-006: una posición guardada dañada no impide abrir el libro
    @Test
    fun corruptSavedPositionIsIgnored() {
        runBlocking { positionRepository.save(bookId, "{no es json", null) }
        launch(copyAsset("minimal.epub")).use { scenario ->
            waitUntil { navigator(scenario) != null }
        }
    }

    // --- Paso de página (RDR-009) ---

    private fun progression(scenario: ActivityScenario<MainActivity>): Double =
        navigator(scenario)?.currentLocator?.value?.locations?.progression ?: -1.0

    private fun screenSize(scenario: ActivityScenario<MainActivity>): Pair<Float, Float> {
        var size = 0f to 0f
        scenario.onActivity { size = it.window.decorView.width.toFloat() to it.window.decorView.height.toFloat() }
        return size
    }

    private fun touch(action: Int, downTime: Long, x: Float, y: Float) {
        val event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, x, y, 0)
        instrumentation.sendPointerSync(event)
        event.recycle()
    }

    private fun tap(x: Float, y: Float) {
        val downTime = SystemClock.uptimeMillis()
        touch(MotionEvent.ACTION_DOWN, downTime, x, y)
        touch(MotionEvent.ACTION_UP, downTime, x, y)
    }

    /** Recorre las posiciones x dadas con el dedo abajo, esperando [stepMs] entre cada una, y suelta. */
    private fun dragThrough(xs: List<Float>, y: Float, stepMs: Long) {
        val downTime = SystemClock.uptimeMillis()
        touch(MotionEvent.ACTION_DOWN, downTime, xs.first(), y)
        for (x in xs.drop(1)) {
            touch(MotionEvent.ACTION_MOVE, downTime, x, y)
            Thread.sleep(stepMs)
        }
        touch(MotionEvent.ACTION_UP, downTime, xs.last(), y)
    }

    private fun swipe(fromX: Float, toX: Float, y: Float) {
        val downTime = SystemClock.uptimeMillis()
        touch(MotionEvent.ACTION_DOWN, downTime, fromX, y)
        for (i in 1..8) {
            touch(MotionEvent.ACTION_MOVE, downTime, fromX + (toX - fromX) * i / 8, y)
            Thread.sleep(16)
        }
        touch(MotionEvent.ACTION_UP, downTime, toX, y)
    }

    // RDR-009: tocar el borde derecho avanza y la captura de la animación desaparece al terminar
    @Test
    fun tappingRightEdgeAdvancesAndEndsTheAnimation() {
        launch(copyAsset("minimal.epub")).use { scenario ->
            waitUntil { navigator(scenario) != null }
            val before = progression(scenario)
            val (w, h) = screenSize(scenario)
            tap(w * 0.9f, h * 0.5f)
            waitUntil { progression(scenario) > before }
            waitUntil { runCatching { compose.onNodeWithTag("page-turn-snapshot").assertDoesNotExist() }.isSuccess }
        }
    }

    // RDR-009: tocar el borde izquierdo retrocede
    @Test
    fun tappingLeftEdgeGoesBack() {
        launch(copyAsset("minimal.epub")).use { scenario ->
            waitUntil { navigator(scenario) != null }
            val (w, h) = screenSize(scenario)
            tap(w * 0.9f, h * 0.5f)
            waitUntil { progression(scenario) > 0.0 }
            val advanced = progression(scenario)
            waitUntil { runCatching { compose.onNodeWithTag("page-turn-snapshot").assertDoesNotExist() }.isSuccess }
            tap(w * 0.1f, h * 0.5f)
            waitUntil { progression(scenario) < advanced }
        }
    }

    // RDR-009: deslizar hacia la izquierda avanza
    @Test
    fun swipingLeftAdvances() {
        launch(copyAsset("minimal.epub")).use { scenario ->
            waitUntil { navigator(scenario) != null }
            val before = progression(scenario)
            val (w, h) = screenSize(scenario)
            swipe(w * 0.8f, w * 0.2f, h * 0.5f)
            waitUntil { progression(scenario) > before }
        }
    }

    // RDR-009: con la animación desactivada el paso de página sigue funcionando, sin captura
    @Test
    fun pageTurnWorksWithAnimationDisabled() {
        runBlocking { settingsRepository.update { it.togglePageAnimation() } }
        launch(copyAsset("minimal.epub")).use { scenario ->
            waitUntil { navigator(scenario) != null }
            val before = progression(scenario)
            val (w, h) = screenSize(scenario)
            tap(w * 0.9f, h * 0.5f)
            waitUntil { progression(scenario) > before }
            compose.onNodeWithTag("page-turn-snapshot").assertDoesNotExist()
        }
    }

    // Un toque en el centro muestra u oculta los controles
    @Test
    fun tappingCenterTogglesControls() {
        launch(copyAsset("minimal.epub")).use { scenario ->
            waitUntil { navigator(scenario) != null }
            compose.onNodeWithText("Índice").assertIsDisplayed()
            val (w, h) = screenSize(scenario)
            tap(w * 0.5f, h * 0.5f)
            waitUntil { runCatching { compose.onNodeWithText("Índice").assertDoesNotExist() }.isSuccess }
            assertTrue(progression(scenario) <= 0.01)
        }
    }

    // RDR-009: un arrastre lento que pasa el umbral se completa al soltar
    @Test
    fun slowDragPastTheThresholdCompletes() {
        launch(copyAsset("minimal.epub")).use { scenario ->
            waitUntil { navigator(scenario) != null }
            val before = progression(scenario)
            val (w, h) = screenSize(scenario)
            dragThrough(List(10) { w * 0.85f - w * 0.5f * it / 9 }, h * 0.5f, stepMs = 50)
            waitUntil { progression(scenario) > before }
            waitUntil { runCatching { compose.onNodeWithTag("page-turn-snapshot").assertDoesNotExist() }.isSuccess }
        }
    }

    // RDR-009: un arrastre corto y lento se cancela al soltar y la página queda como estaba
    @Test
    fun shortSlowDragCancelsAndStaysOnTheSamePage() {
        launch(copyAsset("minimal.epub")).use { scenario ->
            waitUntil { navigator(scenario) != null }
            val before = progression(scenario)
            val (w, h) = screenSize(scenario)
            dragThrough(List(8) { w * 0.8f - w * 0.1f * it / 7 }, h * 0.5f, stepMs = 70)
            Thread.sleep(1500)
            waitUntil { runCatching { compose.onNodeWithTag("page-turn-snapshot").assertDoesNotExist() }.isSuccess }
            assertEquals(before, progression(scenario), 1e-9)
        }
    }

    // RDR-009: lo que cuenta es dónde se suelta, no cuánto se arrastró: ir y volver cancela
    @Test
    fun draggingOutAndBackBeforeReleaseCancels() {
        launch(copyAsset("minimal.epub")).use { scenario ->
            waitUntil { navigator(scenario) != null }
            val before = progression(scenario)
            val (w, h) = screenSize(scenario)
            val out = List(8) { w * 0.85f - w * 0.5f * it / 7 }
            val back = List(8) { w * 0.35f + w * 0.5f * (it + 1) / 8 }
            dragThrough(out + back, h * 0.5f, stepMs = 60)
            Thread.sleep(1500)
            waitUntil { runCatching { compose.onNodeWithTag("page-turn-snapshot").assertDoesNotExist() }.isSuccess }
            assertEquals(before, progression(scenario), 1e-9)
        }
    }

    // RDR-009: pasar de página seguido, sin esperar a que termine la animación anterior, sigue avanzando
    @Test
    fun rapidSwipesKeepAdvancing() {
        launch(copyAsset("minimal.epub")).use { scenario ->
            waitUntil { navigator(scenario) != null }
            val (w, h) = screenSize(scenario)
            val start = progression(scenario)
            swipe(w * 0.8f, w * 0.2f, h * 0.5f)
            Thread.sleep(2000)
            val afterOne = progression(scenario)
            val page = afterOne - start
            assertTrue("una página debe avanzar: $start -> $afterOne", page > 0)
            repeat(3) {
                swipe(w * 0.8f, w * 0.2f, h * 0.5f)
                Thread.sleep(50)
            }
            Thread.sleep(2500)
            val afterRapid = progression(scenario)
            assertTrue("3 swipes seguidos deben avanzar ~3 páginas: $afterOne -> $afterRapid (página=$page)", afterRapid - afterOne > page * 2.5)
        }
    }
}
