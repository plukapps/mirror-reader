package com.pluk.reader.reader

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pluk.reader.R
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.shared.ExperimentalReadiumApi
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@OptIn(ExperimentalReadiumApi::class)
@RunWith(AndroidJUnit4::class)
class ReaderActivityTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context: Context = instrumentation.targetContext

    @get:Rule
    val compose = createEmptyComposeRule()

    @Before
    fun cleanState() {
        context.getSharedPreferences("reader_locators", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("reader_settings", Context.MODE_PRIVATE).edit().clear().commit()
    }

    private fun copyAsset(name: String): Uri {
        val file = File(context.cacheDir, name)
        instrumentation.context.assets.open(name).use { input ->
            file.outputStream().use { input.copyTo(it) }
        }
        return Uri.fromFile(file)
    }

    private fun readerIntent(uri: Uri) =
        Intent(context, ReaderActivity::class.java).setData(uri)

    private fun waitUntil(timeoutMs: Long = 15_000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return
            Thread.sleep(100)
        }
        throw AssertionError("Condición no cumplida en ${timeoutMs}ms")
    }

    private fun hasNavigator(scenario: ActivityScenario<ReaderActivity>): Boolean {
        var found = false
        scenario.onActivity {
            found = it.supportFragmentManager.findFragmentByTag(ReaderActivity.NAVIGATOR_TAG) != null
        }
        return found
    }

    // RDR-007
    @Test
    fun showsNavigatorForValidEpub() {
        ActivityScenario.launch<ReaderActivity>(readerIntent(copyAsset("minimal.epub"))).use { scenario ->
            waitUntil { hasNavigator(scenario) }
        }
    }

    // Foco de revisión 2: rotación o recreación en plena lectura
    @Test
    fun keepsNavigatorAfterRecreation() {
        ActivityScenario.launch<ReaderActivity>(readerIntent(copyAsset("minimal.epub"))).use { scenario ->
            waitUntil { hasNavigator(scenario) }
            scenario.recreate()
            waitUntil { hasNavigator(scenario) }
        }
    }

    // LIB-002, foco de revisión 1
    @Test
    fun showsErrorMessageForCorruptFile() {
        val file = File(context.cacheDir, "corrupto.epub").apply { writeText("esto no es un epub") }
        ActivityScenario.launch<ReaderActivity>(readerIntent(Uri.fromFile(file))).use { scenario ->
            waitUntil {
                var visible = false
                scenario.onActivity {
                    val error = it.findViewById<TextView>(R.id.error)
                    visible = error.isVisible && error.text.isNotBlank()
                }
                visible
            }
            assertTrue(!hasNavigator(scenario))
        }
    }

    // RDR-001: el ajuste llega al navegador
    @Test
    fun scrollSettingIsAppliedToNavigator() {
        ActivityScenario.launch<ReaderActivity>(readerIntent(copyAsset("minimal.epub"))).use { scenario ->
            waitUntil { hasNavigator(scenario) }
            scenario.onActivity { activity ->
                ViewModelProvider(activity)[ReaderViewModel::class.java].toggleScroll()
            }
            waitUntil {
                var scroll = false
                scenario.onActivity { activity ->
                    val navigator = activity.supportFragmentManager
                        .findFragmentByTag(ReaderActivity.NAVIGATOR_TAG) as EpubNavigatorFragment
                    scroll = navigator.settings.value.scroll
                }
                scroll
            }
        }
    }

    // RDR-004: el índice lista los capítulos y navega al elegido
    @Test
    fun tocDialogListsChaptersAndNavigates() {
        ActivityScenario.launch<ReaderActivity>(readerIntent(copyAsset("minimal.epub"))).use { scenario ->
            waitUntil { hasNavigator(scenario) }
            compose.onNodeWithText("Índice").performClick()
            compose.onNodeWithText("Capítulo 1").assertIsDisplayed()
            compose.onNodeWithText("Capítulo 2").assertIsDisplayed().performClick()
            waitUntil {
                var href = ""
                scenario.onActivity { activity ->
                    val navigator = activity.supportFragmentManager
                        .findFragmentByTag(ReaderActivity.NAVIGATOR_TAG) as EpubNavigatorFragment
                    href = navigator.currentLocator.value.href.toString()
                }
                href.endsWith("ch2.xhtml")
            }
        }
    }
}
