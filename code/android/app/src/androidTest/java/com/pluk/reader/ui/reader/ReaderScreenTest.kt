package com.pluk.reader.ui.reader

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.fragment.app.FragmentContainerView
import androidx.fragment.app.findFragment
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pluk.reader.MainActivity
import com.pluk.reader.domain.repository.PositionRepository
import com.pluk.reader.domain.repository.SettingsRepository
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.util.Url
import org.readium.r2.shared.util.mediatype.MediaType
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
    private val bookId = "urn:uuid:11111111-2222-3333-4444-555555555555"

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

    // Sin libro, se ve la pantalla de inicio
    @Test
    fun withoutBookShowsHome() {
        launch().use {
            compose.onNodeWithText("Abrir EPUB").assertIsDisplayed()
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

    // RDR-001: el ajuste llega al navegador
    @Test
    fun scrollSettingIsAppliedToNavigator() {
        launch(copyAsset("minimal.epub")).use { scenario ->
            waitUntil { navigator(scenario) != null }
            runBlocking { settingsRepository.update { it.toggleScroll() } }
            waitUntil { navigator(scenario)?.settings?.value?.scroll == true }
        }
    }

    // RDR-008 (local): los ajustes guardados se aplican al abrir el libro
    @Test
    fun savedSettingsAreAppliedWhenOpeningBook() {
        runBlocking { settingsRepository.update { it.toggleScroll() } }
        launch(copyAsset("minimal.epub")).use { scenario ->
            waitUntil { navigator(scenario)?.settings?.value?.scroll == true }
        }
    }

    // RDR-004: el índice lista los capítulos y navega al elegido
    @Test
    fun tocDialogListsChaptersAndNavigates() {
        launch(copyAsset("minimal.epub")).use { scenario ->
            waitUntil { navigator(scenario) != null }
            compose.onNodeWithText("Índice").performClick()
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
        runBlocking { positionRepository.save(bookId, "{no es json") }
        launch(copyAsset("minimal.epub")).use { scenario ->
            waitUntil { navigator(scenario) != null }
        }
    }
}
